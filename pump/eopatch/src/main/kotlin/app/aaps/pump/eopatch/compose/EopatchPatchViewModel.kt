package app.aaps.pump.eopatch.compose

import android.content.res.Resources
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.SiteLocationStepHost
import app.aaps.pump.eopatch.CommonUtils
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.core.define.IPatchConstant
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult.TEST_SUCCESS
import app.aaps.pump.eopatch.extension.getDiffDays
import app.aaps.pump.eopatch.extension.subscribeDefault
import app.aaps.pump.eopatch.extension.subscribeEmpty
import app.aaps.pump.eopatch.extension.takeOne
import app.aaps.pump.eopatch.keys.EopatchIntKey
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.PatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@HiltViewModel
@Stable
class EopatchPatchViewModel @Inject constructor(
    val rh: ResourceHelper,
    val patchManager: IPatchManager,
    private val patchManagerExecutor: PatchManagerExecutor,
    private val preferenceManager: PreferenceManager,
    private val patchConfig: PatchConfig,
    private val alarmRegistry: IAlarmRegistry,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxAction: RxAction,
    private val preferences: Preferences,
    private val insulinManager: InsulinManager,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer
) : ViewModel(), SiteLocationStepHost {

    companion object {

        private const val MAX_ELAPSED_MILLIS_AFTER_EXPIRATION = -12L * 60 * 60 * 1000
    }

    private val disposables = CompositeDisposable()

    var forceDiscard = false
    var isInAlarmHandling = false
    var connectionTryCnt = 0

    val patchState: PatchState = preferenceManager.patchState

    // State flows
    private val _patchStep = MutableStateFlow<PatchStep?>(null)
    val patchStep: StateFlow<PatchStep?> = _patchStep

    private val _setupStep = MutableStateFlow<SetupStep?>(null)
    val setupStep: StateFlow<SetupStep?> = _setupStep

    private val _title = MutableStateFlow(R.string.string_activate_patch)
    val title: StateFlow<Int> = _title

    private val _safetyCheckProgress = MutableStateFlow(0)
    val safetyCheckProgress: StateFlow<Int> = _safetyCheckProgress

    private val _isActivated = MutableStateFlow(patchConfig.isActivated)
    val isActivated: StateFlow<Boolean> = _isActivated

    // Insulin selection
    private val _availableInsulins = MutableStateFlow<List<ICfg>>(emptyList())
    val availableInsulins: StateFlow<List<ICfg>> = _availableInsulins.asStateFlow()
    private val _selectedInsulin = MutableStateFlow<ICfg?>(null)
    val selectedInsulin: StateFlow<ICfg?> = _selectedInsulin.asStateFlow()
    private val _activeInsulinLabel = MutableStateFlow<String?>(null)
    val activeInsulinLabel: StateFlow<String?> = _activeInsulinLabel.asStateFlow()

    // Site location
    private val _siteLocation = MutableStateFlow(TE.Location.NONE)
    override val siteLocation: StateFlow<TE.Location> = _siteLocation.asStateFlow()
    private val _siteArrow = MutableStateFlow(TE.Arrow.NONE)
    override val siteArrow: StateFlow<TE.Arrow> = _siteArrow.asStateFlow()
    private var siteRotationEntriesCache: List<TE> = emptyList()

    val showSiteLocationStep: Boolean
        get() = preferences.get(BooleanKey.SiteRotationManagePump)

    // Ticks every second to drive recomposition of time-dependent properties
    private val _expirationTick = MutableStateFlow(0L)
    val expirationTick: StateFlow<Long> = _expirationTick

    private val _isCommChecking = MutableStateFlow(false)
    val isCommChecking: StateFlow<Boolean> = _isCommChecking

    private val _events = MutableSharedFlow<PatchEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<PatchEvent> = _events

    val isBolusActive: Boolean get() = preferenceManager.patchState.isBolusActive
    val isConnected: Boolean get() = patchManagerExecutor.patchConnectionState.isConnected

    // Patch remaining insulin
    val patchRemainedInsulin: Int
        get() = if (_isActivated.value) {
            preferenceManager.patchState.remainedInsulin.let { insulin ->
                when {
                    insulin > 50f -> 51
                    insulin < 1f  -> 0
                    else          -> insulin.roundToInt()
                }
            }
        } else 0

    // Expiration time
    val patchExpirationTimestamp: Long get() = patchConfig.patchExpiredTime

    val patchRemainedDays: Int get() = patchConfig.patchExpiredTime.getDiffDays().toInt()

    val patchRemainedTime: String get() = patchConfig.patchExpiredTime.diffTime(MAX_ELAPSED_MILLIS_AFTER_EXPIRATION)

    val programEnabledMessage: String get() = rh.gs(R.string.patch_basal_schedule_desc_1, "Basal 1")

    private val isBonded: Boolean get() = !patchConfig.lifecycleEvent.isShutdown

    private val isSubStepRunning: Boolean get() = patchConfig.lifecycleEvent.isSubStepRunning

    private val initPatchStepIsSafeDeactivation: Boolean get() = mInitPatchStep?.isSafeDeactivation == true

    private val initPatchStepIsCheckConnection: Boolean get() = mInitPatchStep?.isCheckConnection == true

    val commCheckCancelLabel: String
        get() = rh.gs(
            when (_patchStep.value) {
                PatchStep.CONNECT_NEW       -> isBonded.takeOne(app.aaps.core.ui.R.string.cancel, R.string.patch_cancel_pairing)
                PatchStep.SAFE_DEACTIVATION -> R.string.patch_forced_discard
                else                        -> app.aaps.core.ui.R.string.cancel
            }
        )

    private var mCommCheckDisposable: Disposable? = null
    private var mOnCommCheckSuccessListener: (() -> Unit)? = null
    private var mOnCommCheckCancelListener: (() -> Unit)? = null
    private var mOnCommCheckDiscardListener: (() -> Unit)? = null
    private var mInitPatchStep: PatchStep? = null
    private val mMaxRetryCount = 3
    private var mRetryCount = 0
    private var mUpdateDisposable: Disposable? = null
    private var mB012UpdateDisposable: Disposable? = null
    private val mB012UpdateSubject = PublishSubject.create<Unit>()

    // Step progress calculation
    val totalSteps: Int
        get() = when (_patchStep.value) {
            PatchStep.SAFE_DEACTIVATION,
            PatchStep.MANUALLY_TURNING_OFF_ALARM,
            PatchStep.DISCARDED, PatchStep.DISCARDED_FOR_CHANGE, PatchStep.DISCARDED_FROM_ALARM -> 3

            else                                                                                -> 9
        }

    val currentStepIndex: Int
        get() = when (_patchStep.value) {
            PatchStep.WAKE_UP                            -> 0
            PatchStep.CONNECT_NEW                        -> 1
            PatchStep.SELECT_INSULIN                     -> 2
            PatchStep.REMOVE_NEEDLE_CAP                  -> 3
            PatchStep.SITE_LOCATION                      -> 4
            PatchStep.REMOVE_PROTECTION_TAPE             -> 5
            PatchStep.SAFETY_CHECK                       -> 6
            PatchStep.ROTATE_KNOB,
            PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR -> 7

            PatchStep.BASAL_SCHEDULE                     -> 8
            PatchStep.SAFE_DEACTIVATION                  -> 0
            PatchStep.MANUALLY_TURNING_OFF_ALARM         -> 1
            PatchStep.DISCARDED, PatchStep.DISCARDED_FOR_CHANGE,
            PatchStep.DISCARDED_FROM_ALARM               -> 2

            else                                         -> 0
        }

    init {
        startB012UpdateSubscription()
        loadSiteRotationEntries()

        disposables.add(
            preferenceManager.observePatchLifeCycle()
                .observeOn(aapsSchedulers.main)
                .subscribe { _isActivated.value = patchConfig.isActivated }
        )
    }

    private fun startB012UpdateSubscription() {
        CommonUtils.dispose(mB012UpdateDisposable)
        mB012UpdateDisposable = mB012UpdateSubject.hide()
            .throttleFirst(500, TimeUnit.MILLISECONDS)
            .delay(100, TimeUnit.MILLISECONDS)
            .filter { isSubStepRunning }
            .observeOn(aapsSchedulers.main)
            .flatMapMaybe { alarmRegistry.remove(AlarmCode.B012) }
            .flatMapMaybe { alarmRegistry.add(AlarmCode.B012, TimeUnit.MINUTES.toMillis(3)) }
            .subscribe()
    }

    fun reset() {
        _patchStep.value = null
        _setupStep.value = null
        _safetyCheckProgress.value = 0
        _expirationTick.value = 0
        forceDiscard = false
        isInAlarmHandling = false
        connectionTryCnt = 0
        mRetryCount = 0
        mInitPatchStep = null
        mOnCommCheckSuccessListener = null
        mOnCommCheckCancelListener = null
        mOnCommCheckDiscardListener = null
        CommonUtils.dispose(mCommCheckDisposable)
        CommonUtils.dispose(mUpdateDisposable)
        startB012UpdateSubscription()
    }

    override fun onCleared() {
        super.onCleared()
        mOnCommCheckSuccessListener = null
        mOnCommCheckCancelListener = null
        mOnCommCheckDiscardListener = null
        CommonUtils.dispose(mCommCheckDisposable)
        CommonUtils.dispose(mUpdateDisposable)
        CommonUtils.dispose(mB012UpdateDisposable)
        disposables.clear()
    }

    // ── Navigation ──────────────────────────────────────────────────────

    fun moveStep(newPatchStep: PatchStep) {
        val oldPatchStep = _patchStep.value

        if (oldPatchStep != newPatchStep) {
            when (newPatchStep) {
                PatchStep.REMOVE_NEEDLE_CAP      -> PatchLifecycleEvent.createRemoveNeedleCap()
                PatchStep.REMOVE_PROTECTION_TAPE -> PatchLifecycleEvent.createRemoveProtectionTape()
                PatchStep.SAFETY_CHECK           -> PatchLifecycleEvent.createSafetyCheck()
                PatchStep.ROTATE_KNOB            -> PatchLifecycleEvent.createRotateKnob()

                PatchStep.WAKE_UP                -> {
                    patchConfig.apply { rotateKnobNeedleSensingError = false }
                    PatchLifecycleEvent.createShutdown()
                }

                PatchStep.CANCEL                 -> {
                    if (!patchConfig.isActivated) PatchLifecycleEvent.createShutdown() else null
                }

                else                             -> null
            }?.let { preferenceManager.updatePatchLifeCycle(it) }
        }

        prepareStep(newPatchStep)
        aapsLogger.info(LTag.PUMP, "moveStep: $oldPatchStep -> $newPatchStep")
    }

    fun initializePatchStep(step: PatchStep?, withAlarmHandle: Boolean = true) {
        mInitPatchStep = prepareStep(step, withAlarmHandle)
        dismissPatchCommCheckDialogInternal(false)
    }

    private fun prepareStep(step: PatchStep?, withAlarmHandle: Boolean = true): PatchStep {
        (step ?: convertToPatchStep(patchConfig.lifecycleEvent.lifeCycle)).let { newStep ->
            when (newStep) {
                PatchStep.SAFE_DEACTIVATION          -> R.string.string_discard_patch
                PatchStep.DISCARDED,
                PatchStep.DISCARDED_FROM_ALARM,
                PatchStep.DISCARDED_FOR_CHANGE       -> R.string.patch_discard_complete_title

                PatchStep.MANUALLY_TURNING_OFF_ALARM -> R.string.patch_manually_turning_off_alarm_title
                PatchStep.WAKE_UP,
                PatchStep.CONNECT_NEW,
                PatchStep.SELECT_INSULIN,
                PatchStep.REMOVE_NEEDLE_CAP,
                PatchStep.SITE_LOCATION,
                PatchStep.REMOVE_PROTECTION_TAPE,
                PatchStep.SAFETY_CHECK,
                PatchStep.ROTATE_KNOB,
                PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR,
                PatchStep.BASAL_SCHEDULE             -> R.string.string_activate_patch

                PatchStep.SETTING_REMINDER_TIME      -> R.string.patch_expiration_reminder_setting_title
                else                                 -> _title.value
            }.let {
                if (_title.value != it) _title.value = it
            }

            _patchStep.value = newStep

            // Terminal steps → emit finish event
            when (newStep) {
                PatchStep.COMPLETE,
                PatchStep.FINISH,
                PatchStep.BACK_TO_HOME,
                PatchStep.CANCEL -> _events.tryEmit(PatchEvent.Finish)

                else             -> Unit
            }

            if (withAlarmHandle) {
                when (newStep) {
                    PatchStep.REMOVE_NEEDLE_CAP,
                    PatchStep.REMOVE_PROTECTION_TAPE, PatchStep.SAFETY_CHECK, PatchStep.ROTATE_KNOB -> {
                        updateIncompletePatchActivationReminder(true)
                    }

                    PatchStep.COMPLETE, PatchStep.BASAL_SCHEDULE                                    -> {
                        val now = System.currentTimeMillis()
                        val expireTimeStamp = patchConfig.expireTimestamp
                        val millisBeforeExpiration = TimeUnit.HOURS.toMillis(preferences.get(EopatchIntKey.ExpirationReminder).toLong())

                        Maybe.just(AlarmCode.B012)
                            .flatMap { alarmRegistry.remove(it) }
                            .flatMap { alarmRegistry.remove(AlarmCode.A020) }
                            .flatMap { alarmRegistry.add(AlarmCode.B000, expireTimeStamp - now - millisBeforeExpiration) }
                            .flatMap { alarmRegistry.add(AlarmCode.B005, expireTimeStamp - now) }
                            .flatMap { alarmRegistry.add(AlarmCode.B006, expireTimeStamp - now + IPatchConstant.SERVICE_TIME_MILLI - TimeUnit.HOURS.toMillis(1)) }
                            .flatMap { alarmRegistry.add(AlarmCode.A003, expireTimeStamp - now + IPatchConstant.SERVICE_TIME_MILLI) }
                            .subscribe()
                    }

                    PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR                                    -> {
                        patchConfig.apply { rotateKnobNeedleSensingError = true }
                        updateIncompletePatchActivationReminder(true)
                    }

                    PatchStep.CANCEL                                                                -> {
                        alarmRegistry.remove(AlarmCode.B012).subscribe()
                    }

                    else                                                                            -> Unit
                }
            }

            return newStep
        }
    }

    private fun convertToPatchStep(lifecycle: PatchLifecycle) = when (lifecycle) {
        PatchLifecycle.SHUTDOWN               -> patchConfig.isDeactivated.takeOne(PatchStep.WAKE_UP, PatchStep.SAFE_DEACTIVATION)
        PatchLifecycle.BONDED                 -> PatchStep.CONNECT_NEW
        PatchLifecycle.REMOVE_NEEDLE_CAP      -> PatchStep.REMOVE_NEEDLE_CAP
        PatchLifecycle.REMOVE_PROTECTION_TAPE -> PatchStep.REMOVE_PROTECTION_TAPE
        PatchLifecycle.SAFETY_CHECK           -> PatchStep.SAFETY_CHECK
        PatchLifecycle.ROTATE_KNOB            -> PatchStep.ROTATE_KNOB
        PatchLifecycle.BASAL_SETTING          -> PatchStep.ROTATE_KNOB
        PatchLifecycle.ACTIVATED              -> PatchStep.SAFE_DEACTIVATION
    }

    // ── Step initialization ─────────────────────────────────────────────

    fun initPatchStep() {
        when (_patchStep.value) {
            PatchStep.WAKE_UP                            -> _setupStep.value = SetupStep.WAKE_UP_READY
            PatchStep.SAFETY_CHECK                       -> _setupStep.value = SetupStep.SAFETY_CHECK_READY
            PatchStep.ROTATE_KNOB,
            PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR -> _setupStep.value = SetupStep.NEEDLE_SENSING_READY

            else                                         -> Unit
        }
    }

    fun onConfirm() {
        when (_patchStep.value) {
            PatchStep.DISCARDED_FOR_CHANGE       -> PatchStep.WAKE_UP
            PatchStep.DISCARDED_FROM_ALARM       -> PatchStep.FINISH

            PatchStep.DISCARDED                  -> {
                if (initPatchStepIsCheckConnection) {
                    mOnCommCheckDiscardListener?.invoke()
                    mOnCommCheckDiscardListener = null
                    null
                } else {
                    PatchStep.BACK_TO_HOME
                }
            }

            PatchStep.MANUALLY_TURNING_OFF_ALARM -> {
                initPatchStepIsSafeDeactivation.takeOne(PatchStep.DISCARDED_FOR_CHANGE, PatchStep.DISCARDED)
            }

            PatchStep.BASAL_SCHEDULE             -> {
                if (!patchManagerExecutor.patchConnectionState.isConnected) {
                    checkCommunication({ moveStep(PatchStep.COMPLETE) }, { moveStep(PatchStep.BASAL_SCHEDULE) })
                    null
                } else {
                    PatchStep.COMPLETE
                }
            }

            else                                 -> null
        }?.let { moveStep(it) }
    }

    // ── Communication check ─────────────────────────────────────────────

    @Synchronized
    fun checkCommunication(
        onSuccessListener: () -> Unit, onCancelListener: (() -> Unit)? = null,
        onDiscardListener: (() -> Unit)? = null, doPreCheck: Boolean = false
    ) {
        if (doPreCheck && patchManagerExecutor.patchConnectionState.isConnected) {
            onSuccessListener.invoke()
            return
        }
        mOnCommCheckSuccessListener = onSuccessListener
        mOnCommCheckCancelListener = onCancelListener
        mOnCommCheckDiscardListener = onDiscardListener
        checkCommunicationInternal()
    }

    fun retryCheckCommunication() {
        updateIncompletePatchActivationReminder()
        checkCommunicationInternal()
    }

    private fun checkCommunicationInternal(timeout: Long = 8000) {
        CommonUtils.dispose(mCommCheckDisposable)

        if (forceDiscard) connectionTryCnt++

        mCommCheckDisposable = if (isBonded) {
            patchManagerExecutor.observePatchConnectionState()
                .timeout(timeout, TimeUnit.MILLISECONDS, Observable.just(BleConnectionState.DISCONNECTED))
                .takeUntil { it == BleConnectionState.CONNECTED }
                .last(BleConnectionState.DISCONNECTED)
                .map { it == BleConnectionState.CONNECTED }
        } else {
            patchManager.scan(timeout)
                .flatMap {
                    if (it.nearestDevice == null) Single.error(Resources.NotFoundException())
                    else Single.just(true)
                }
                .retry(1)
        }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .onErrorReturnItem(false)
            .doOnSubscribe { _isCommChecking.value = true }
            .doFinally { onCommCheckComplete() }
            .doOnError { aapsLogger.error(LTag.PUMP, it.message ?: "Error") }
            .subscribeDefault(aapsLogger) { success ->
                if (!success) {
                    _isCommChecking.value = false
                    _events.tryEmit(
                        PatchEvent.ShowCommError(
                            isForcedDiscard = _patchStep.value?.isSafeDeactivation == true || connectionTryCnt >= 2
                        )
                    )
                }
            }
    }

    private fun onCommCheckComplete() {
        if (patchManagerExecutor.patchConnectionState.isConnected) {
            if (isBonded) {
                _isCommChecking.value = false
                _events.tryEmit(PatchEvent.ShowBondedDialog())
            } else {
                dismissPatchCommCheckDialogInternal(true)
            }
        }
        // If not connected, the error was already handled in subscribeDefault
    }

    fun dismissPatchCommCheckDialogInternal(doOnSuccessOrCancel: Boolean? = null) {
        _isCommChecking.value = false
        doOnSuccessOrCancel?.let {
            if (it) mOnCommCheckSuccessListener?.invoke()
            else mOnCommCheckCancelListener?.invoke()
        }
        mOnCommCheckSuccessListener = null
        mOnCommCheckCancelListener = null
    }

    fun cancelPatchCommCheck() {
        CommonUtils.dispose(mCommCheckDisposable)
        updateIncompletePatchActivationReminder()
        dismissPatchCommCheckDialogInternal(false)
        connectionTryCnt = 0
    }

    // ── Deactivation ────────────────────────────────────────────────────

    fun changePatch() {
        _events.tryEmit(PatchEvent.ShowChangePatchDialog())
    }

    fun discardPatchWithCommCheck() {
        checkCommunication({ discardPatchInternal() }, doPreCheck = true)
    }

    fun deactivatePatch() {
        if (patchManagerExecutor.patchConnectionState.isConnected) {
            deactivate(false) {
                try {
                    moveStep(PatchStep.DISCARDED_FOR_CHANGE)
                } catch (_: IllegalStateException) {
                    _events.tryEmit(PatchEvent.Finish)
                }
            }
        } else {
            mOnCommCheckSuccessListener = {
                deactivate(true) { moveStep(PatchStep.DISCARDED_FOR_CHANGE) }
            }
            _isCommChecking.value = true
            disposables.add(
                Single.timer(10, TimeUnit.SECONDS)
                    .doFinally { onCommCheckComplete() }
                    .subscribe()
            )
        }
    }

    fun discardPatch() {
        updateIncompletePatchActivationReminder()
        discardPatchInternal()
    }

    private fun discardPatchInternal() {
        val isBolusActive = preferenceManager.patchState.isBolusActive

        if (_patchStep.value == PatchStep.SAFE_DEACTIVATION && !isBolusActive) {
            deactivate(true) {
                dismissPatchCommCheckDialogInternal()
                moveStep(PatchStep.MANUALLY_TURNING_OFF_ALARM)
            }
            return
        }

        _events.tryEmit(PatchEvent.ShowDiscardDialog())
    }

    @Synchronized
    fun deactivate(force: Boolean, onSuccessListener: () -> Unit) {
        disposables.add(
            patchManagerExecutor.deactivate(6000, force)
                .doOnSubscribe {
                    _isCommChecking.value = true
                }
                .doFinally {
                    _isCommChecking.value = false
                }
                .subscribeDefault(aapsLogger) { status ->
                    if (status.isDeactivated) {
                        onSuccessListener.invoke()
                    } else {
                        rxAction.runOnMainThread({
                                                     checkCommunication({ deactivate(false, onSuccessListener) }, { _events.tryEmit(PatchEvent.Finish) })
                                                 }, 100)
                    }
                }
        )
    }

    // ── BLE Operations ──────────────────────────────────────────────────

    @Synchronized
    fun retryScan() {
        if (mRetryCount <= mMaxRetryCount) {
            startScanInternal()
        } else {
            moveStep(PatchStep.WAKE_UP)
        }
    }

    @Synchronized
    fun startScan() {
        if (isBonded) {
            getPatchInfo()
        } else {
            mRetryCount = 0
            startScanInternal()
        }
    }

    private fun startScanInternal() {
        disposables.add(
            patchManager.scan(5000)
                .flatMap {
                    val nearest = it.nearestDevice
                    if (nearest == null) Single.error(Resources.NotFoundException())
                    else Single.just(nearest)
                }
                .onErrorReturnItem("")
                .doOnSubscribe { updateSetupStep(SetupStep.SCAN_STARTED) }
                .subscribeDefault(aapsLogger) {
                    if (it.isNotEmpty()) startBond(it)
                    else updateSetupStep(SetupStep.SCAN_FAILED)
                }
        )
    }

    @Synchronized
    private fun startBond(scannedMacAddress: String) {
        aapsLogger.info(LTag.PUMP, "startBond: $scannedMacAddress")

        disposables.add(
            patchManagerExecutor.startBond(scannedMacAddress)
                .doOnSubscribe { updateSetupStep(SetupStep.BONDING_STARTED) }
                .filter { result -> result }
                .toSingle()
                .doOnSuccess { preferenceManager.updatePatchLifeCycle(PatchLifecycleEvent.createBonded()) }
                .doOnError {
                    if (it is TimeoutException) moveStep(PatchStep.WAKE_UP)
                    else updateSetupStep(SetupStep.BONDING_FAILED)
                }
                .subscribeDefault(aapsLogger) {
                    if (it) getPatchInfo()
                    else updateSetupStep(SetupStep.BONDING_FAILED)
                }
        )
    }

    @Synchronized
    fun getPatchInfo(timeout: Long = 60000) {
        disposables.add(
            patchManagerExecutor.getPatchInfo(timeout)
                .doOnSubscribe { updateSetupStep(SetupStep.GET_PATCH_INFO_STARTED) }
                .onErrorReturnItem(false)
                .subscribeDefault(aapsLogger) {
                    if (it) selfTest(delayMs = 1000)
                    else updateSetupStep(SetupStep.GET_PATCH_INFO_FAILED)
                }
        )
    }

    @Synchronized
    fun selfTest(timeout: Long = 20000, delayMs: Long = 0) {
        rxAction.runOnMainThread({
                                     disposables.add(
                                         patchManagerExecutor.selfTest(timeout)
                                             .doOnSubscribe { updateSetupStep(SetupStep.SELF_TEST_STARTED) }
                                             .map { it == TEST_SUCCESS }
                                             .onErrorReturnItem(false)
                                             .subscribeDefault(aapsLogger) {
                                                 if (it) moveStep(PatchStep.SELECT_INSULIN)
                                                 else if (!patchManagerExecutor.patchConnectionState.isConnected) updateSetupStep(SetupStep.SELF_TEST_FAILED)
                                             }
                                     )
                                 }, delayMs)
    }

    @Synchronized
    fun retrySafetyCheck() {
        if (mRetryCount <= mMaxRetryCount) {
            startSafetyCheckInternal()
        } else {
            moveStep(PatchStep.SELECT_INSULIN)
        }
    }

    @Synchronized
    fun startSafetyCheck() {
        mRetryCount = 0
        startSafetyCheckInternal()
    }

    private fun startSafetyCheckInternal() {
        disposables.add(
            patchManagerExecutor.startPriming(10000, 100)
                .doOnSubscribe {
                    _safetyCheckProgress.value = 0
                    updateSetupStep(SetupStep.SAFETY_CHECK_STARTED)
                }
                .doOnNext { _safetyCheckProgress.value = it.toInt() }
                .doOnError { updateSetupStep(SetupStep.SAFETY_CHECK_FAILED) }
                .doOnComplete { moveStep(PatchStep.ROTATE_KNOB) }
                .subscribeEmpty()
        )
    }

    @Synchronized
    fun startNeedleSensing() {
        disposables.add(
            patchManagerExecutor.checkNeedleSensing(20000)
                .toObservable()
                .debounce(500, TimeUnit.MILLISECONDS)
                .doOnSubscribe {
                    _isCommChecking.value = true
                    updateSetupStep(SetupStep.NEEDLE_SENSING_STARTED)
                }
                .onErrorReturnItem(false)
                .subscribeDefault(aapsLogger) {
                    if (it) {
                        startActivation()
                    } else {
                        if (!patchManagerExecutor.patchConnectionState.isConnected) {
                            updateSetupStep(SetupStep.NEEDLE_SENSING_FAILED)
                        }
                        _isCommChecking.value = false
                    }
                }
        )
    }

    @Synchronized
    fun startActivation() {
        disposables.add(
            patchManager.patchActivation(20000)
                .doOnSubscribe {
                    _isCommChecking.value = true
                    updateSetupStep(SetupStep.ACTIVATION_STARTED)
                }
                .doFinally { _isCommChecking.value = false }
                .onErrorReturnItem(false)
                .subscribeDefault(aapsLogger) {
                    if (it) moveStep(PatchStep.COMPLETE)
                    else updateSetupStep(SetupStep.ACTIVATION_FAILED)
                }
        )
    }

    fun updateIncompletePatchActivationReminder(forced: Boolean = false) {
        if (forced || isSubStepRunning) {
            mB012UpdateSubject.onNext(Unit)
        }
    }

    fun updateExpirationTime() {
        CommonUtils.dispose(mUpdateDisposable)
        mUpdateDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .takeUntil { !patchConfig.isActivated }
            .subscribeDefault(aapsLogger) { _expirationTick.value = it }
    }

    private fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${_setupStep.value}, newSetupStep: $newSetupStep")
        _setupStep.value = newSetupStep
    }

    private fun Long.diffTime(maxElapsed: Long): String {
        val current = System.currentTimeMillis()
        return abs((this - current).let { (it > maxElapsed).takeOne(it, maxElapsed) }).let { millis ->
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis - TimeUnit.HOURS.toMillis(hours))
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis - TimeUnit.HOURS.toMillis(hours) - TimeUnit.MINUTES.toMillis(minutes))
            (this < current).takeOne("- ", "") + String.format(Locale.getDefault(), "%02d:%02d:%02d", hours % 24, minutes, seconds)
        }
    }

    enum class SetupStep {
        WAKE_UP_READY,
        SCAN_STARTED, SCAN_FAILED,
        BONDING_STARTED, BONDING_FAILED,
        GET_PATCH_INFO_STARTED, GET_PATCH_INFO_FAILED,
        SELF_TEST_STARTED, SELF_TEST_FAILED,
        SAFETY_CHECK_READY, SAFETY_CHECK_STARTED, SAFETY_CHECK_FAILED,
        NEEDLE_SENSING_READY, NEEDLE_SENSING_STARTED, NEEDLE_SENSING_FAILED,
        ACTIVATION_STARTED, ACTIVATION_FAILED
    }

    // Back handler logic
    fun handleBack() {
        _events.tryEmit(PatchEvent.Finish)
    }

    fun handleComplete() {
        _events.tryEmit(PatchEvent.Finish)
    }

    fun handleCancel() {
        moveStep(PatchStep.CANCEL)
    }

    fun forceResetPatchState() {
        _events.tryEmit(PatchEvent.ShowForceResetDialog)
    }

    fun executeForceReset() {
        // Remove BT bond first (while mac is still known), then clear state
        patchManagerExecutor.updateMacAddress("", false)
        patchConfig.updateDeactivated()
        preferenceManager.flushPatchConfig()
        _events.tryEmit(PatchEvent.Finish)
    }

    // region Insulin selection

    fun loadInsulins() {
        if (_availableInsulins.value.isNotEmpty()) return
        viewModelScope.launch {
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
            val current = insulins.find { it.insulinLabel == activeLabel } ?: insulins.firstOrNull()
            _availableInsulins.value = insulins
            _selectedInsulin.value = current
            _activeInsulinLabel.value = activeLabel
        }
    }

    fun selectInsulin(iCfg: ICfg) {
        _selectedInsulin.value = iCfg
    }

    fun executeInsulinProfileSwitch() {
        val selected = _selectedInsulin.value ?: return
        val activeLabel = _activeInsulinLabel.value
        if (selected.insulinLabel == activeLabel) return
        viewModelScope.launch {
            profileFunction.createProfileSwitchWithNewInsulin(selected, Sources.EOPatch2)
        }
    }

    // endregion

    // region SiteLocationStepHost

    override fun updateSiteLocation(location: TE.Location) {
        _siteLocation.value = location
    }

    override fun updateSiteArrow(arrow: TE.Arrow) {
        _siteArrow.value = arrow
    }

    override fun completeSiteLocation() {
        moveStep(PatchStep.REMOVE_PROTECTION_TAPE)
    }

    override fun skipSiteLocation() {
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        moveStep(PatchStep.REMOVE_PROTECTION_TAPE)
    }

    override fun bodyType(): BodyType =
        BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))

    override fun siteRotationEntries(): List<TE> = siteRotationEntriesCache

    private fun loadSiteRotationEntries() {
        viewModelScope.launch {
            siteRotationEntriesCache = persistenceLayer.getTherapyEventDataFromTime(
                System.currentTimeMillis() - T.days(45).msecs(), false
            ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
        }
    }

    fun saveSiteLocationToTherapyEvent(activationTimestamp: Long) {
        val location = _siteLocation.value.takeIf { it != TE.Location.NONE }
        val arrow = _siteArrow.value.takeIf { it != TE.Arrow.NONE }
        if (location != null || arrow != null) {
            viewModelScope.launch {
                try {
                    val entries = persistenceLayer.getTherapyEventDataFromToTime(activationTimestamp, activationTimestamp)
                        .filter { it.type == TE.Type.CANNULA_CHANGE }
                    entries.firstOrNull()?.let { te ->
                        persistenceLayer.insertOrUpdateTherapyEvent(te.copy(location = location, arrow = arrow))
                    }
                } catch (_: Exception) {
                    // location is optional
                }
            }
        }
    }

    // endregion
}
