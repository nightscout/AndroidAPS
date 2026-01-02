package app.aaps.pump.eopatch.ui.viewmodel

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.CommonUtils
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.EventType
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
import app.aaps.pump.eopatch.ui.EoBaseNavigator
import app.aaps.pump.eopatch.ui.event.SingleLiveEvent
import app.aaps.pump.eopatch.ui.event.UIEvent
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.ACTIVATION_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.ACTIVATION_STARTED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.BONDING_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.BONDING_STARTED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.GET_PATCH_INFO_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.GET_PATCH_INFO_STARTED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.NEEDLE_SENSING_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.NEEDLE_SENSING_READY
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.NEEDLE_SENSING_STARTED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SAFETY_CHECK_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SAFETY_CHECK_READY
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SAFETY_CHECK_STARTED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SCAN_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SCAN_STARTED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SELF_TEST_FAILED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.SELF_TEST_STARTED
import app.aaps.pump.eopatch.ui.viewmodel.EopatchViewModel.SetupStep.WAKE_UP_READY
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.PatchState
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

class EopatchViewModel @Inject constructor(
    private val rh: ResourceHelper,
    val patchManager: IPatchManager,
    private val patchManagerExecutor: PatchManagerExecutor,
    private val preferenceManager: PreferenceManager,
    private val patchConfig: PatchConfig,
    private val alarmRegistry: IAlarmRegistry,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxAction: RxAction,
    private val preferences: Preferences
) : EoBaseViewModel<EoBaseNavigator>() {

    companion object {

        private const val MAX_ELAPSED_MILLIS_AFTER_EXPIRATION = -12L * 60 * 60 * 1000
    }

    var forceDiscard = false
    var isInAlarmHandling = false
    var connectionTryCnt = 0

    val patchState: PatchState = preferenceManager.patchState

    private val _isActivated = MutableLiveData(patchConfig.isActivated)

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    val patchStep = MutableLiveData<PatchStep>()

    val isActivated = MutableLiveData(patchConfig.isActivated)
    val isBolusActive = preferenceManager.patchState.isBolusActive
    val isConnected = patchManagerExecutor.patchConnectionState.isConnected

    val patchRemainedInsulin: LiveData<Int>
        get() = _isActivated.map {
            it.takeOne(preferenceManager.patchState.remainedInsulin.let { insulin ->
                when {
                    insulin > 50f -> 51
                    insulin < 1f  -> 0
                    else          -> insulin.roundToInt()
                }
            }, 0)
        }

    private val _patchExpirationTimestamp = MutableLiveData(patchConfig.patchExpiredTime)

    val patchRemainedDays: LiveData<Int>
        get() = _patchExpirationTimestamp.map {
            it.getDiffDays().toInt()
        }

    val patchRemainedTime: LiveData<String>
        get() = _patchExpirationTimestamp.map {
            it.diffTime(MAX_ELAPSED_MILLIS_AFTER_EXPIRATION)
        }

    private val _title = MutableLiveData<Int>()
    val title: LiveData<Int>
        get() = _title

    private val _safetyCheckProgress = MutableLiveData(0)
    val safetyCheckProgress: LiveData<Int>
        get() = _safetyCheckProgress

    private val _isCommCheckFailed = MutableLiveData(false)
    private val isCommCheckFailed: LiveData<Boolean>
        get() = _isCommCheckFailed

    private val isBonded: Boolean
        get() = !patchConfig.lifecycleEvent.isShutdown

    val commCheckCancelLabel: LiveData<String>
        get() = patchStep.map {
            rh.gs(
                when (it) {
                    PatchStep.CONNECT_NEW       -> {
                        isBonded.takeOne(R.string.cancel, R.string.patch_cancel_pairing)
                    }

                    PatchStep.SAFE_DEACTIVATION -> R.string.patch_forced_discard
                    else                        -> R.string.cancel
                }
            )
        }

    val programEnabledMessage: String
        get() = rh.gs(R.string.patch_basal_schedule_desc_1, "기초1")

    private val _isDiscardedWithNotConn = MutableLiveData(false)
    val isDiscardedWithNotConn: LiveData<Boolean>
        get() = _isDiscardedWithNotConn

    private val isSubStepRunning: Boolean
        get() = patchConfig.lifecycleEvent.isSubStepRunning

    private val initPatchStepIsSafeDeactivation: Boolean
        get() = mInitPatchStep?.isSafeDeactivation == true

    private val initPatchStepIsCheckConnection: Boolean
        get() = mInitPatchStep?.isCheckConnection == true

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

    init {
        mB012UpdateDisposable = mB012UpdateSubject.hide()
            .throttleFirst(500, TimeUnit.MILLISECONDS)
            .delay(100, TimeUnit.MILLISECONDS)
            .filter { isSubStepRunning }
            .observeOn(aapsSchedulers.main)
            .flatMapMaybe { alarmRegistry.remove(AlarmCode.B012) }
            .flatMapMaybe { alarmRegistry.add(AlarmCode.B012, TimeUnit.MINUTES.toMillis(3)) }
            .subscribe()

        preferenceManager.observePatchLifeCycle()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                isActivated.value = patchConfig.isActivated
            }
            .addTo()
    }

    private fun Long.diffTime(maxElapsed: Long): String {
        val current = System.currentTimeMillis()

        return abs((this - current).let { (it > maxElapsed).takeOne(it, maxElapsed) }).let { millis ->
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(
                millis
                    - TimeUnit.HOURS.toMillis(hours)
            )
            val seconds = TimeUnit.MILLISECONDS.toSeconds(
                millis
                    - TimeUnit.HOURS.toMillis(hours) - TimeUnit.MINUTES.toMillis(minutes)
            )

            (this < current).takeOne("- ", "") + String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d", hours % 24, minutes, seconds
            )
        }
    }

    fun updateExpirationTime() {
        CommonUtils.dispose(mUpdateDisposable)

        mUpdateDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .takeUntil { !patchConfig.isActivated }
            .subscribeDefault(aapsLogger) {
                _patchExpirationTimestamp.value = patchConfig.patchExpiredTime
            }
    }

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

        if (forceDiscard)
            connectionTryCnt++

        mCommCheckDisposable = if (isBonded) {
            patchManagerExecutor.observePatchConnectionState()
                .timeout(
                    timeout, TimeUnit.MILLISECONDS,
                    Observable.just(BleConnectionState.DISCONNECTED)
                )
                .takeUntil { it == BleConnectionState.CONNECTED }
                .last(BleConnectionState.DISCONNECTED)
                .map { it == BleConnectionState.CONNECTED }
        } else {
            patchManager.scan(timeout)
                .flatMap {
                    if (it.nearestDevice == null)
                        Single.error(Resources.NotFoundException())
                    else
                        Single.just(true)
                }
                .retry(1)
        }
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .onErrorReturnItem(false)
            .doOnSubscribe { showPatchCommCheckDialog() }
            .doFinally { dismissPatchCommCheckDialog() }
            .doOnError { aapsLogger.error(LTag.PUMP, it.message ?: "Error") }
            .subscribeDefault(aapsLogger) {
                _isCommCheckFailed.value = !it
            }
    }

    private fun showPatchCommCheckDialog(defaultFailedCondition: Boolean = false, @StringRes title: Int = R.string.string_connecting) {
        _isCommCheckFailed.postValue(defaultFailedCondition)
        _eventHandler.postValue(UIEvent(EventType.SHOW_PATCH_COMM_DIALOG).apply {
            value = title
        })
    }

    fun dismissPatchCommCheckDialogInternal(doOnSuccessOrCancel: Boolean? = null) {
        _eventHandler.postValue(UIEvent(EventType.DISMISS_PATCH_COMM_DIALOG))
        doOnSuccessOrCancel?.let {
            if (it) {
                mOnCommCheckSuccessListener?.invoke()
            } else {
                mOnCommCheckCancelListener?.invoke()
            }
        }
        mOnCommCheckSuccessListener = null
        mOnCommCheckCancelListener = null
    }

    private fun dismissPatchCommCheckDialog() {
        if (isCommCheckFailed.value == false) {
            if (isBonded) {
                _eventHandler.postValue(UIEvent(EventType.SHOW_BONDED_DIALOG))
            } else {
                dismissPatchCommCheckDialogInternal(true)
                // _eventHandler.postValue(Event(UserEvent.DISMISS_PATCH_COMM_DIALOG))
            }
        } else {
            // dismissPatchCommCheckDialogInternal(false)
            _eventHandler.postValue(UIEvent(EventType.DISMISS_PATCH_COMM_DIALOG))
            _eventHandler.postValue(UIEvent(EventType.SHOW_PATCH_COMM_ERROR_DIALOG))
        }
    }

    fun cancelPatchCommCheck() {
        CommonUtils.dispose(mCommCheckDisposable)
        updateIncompletePatchActivationReminder()
        dismissPatchCommCheckDialogInternal(false)
        connectionTryCnt = 0
    }

    @Synchronized
    private fun showProgressDialog(@StringRes label: Int) {
        _eventHandler.postValue(UIEvent(EventType.SHOW_PATCH_COMM_DIALOG).apply {
            value = label
        })
        // if (mProgressDialog == null) {
        //     mProgressDialog = PatchProgressDialog()
        //
        //     mProgressDialog?.let {
        //         navigator?.showDialog(it.apply {
        //             setLabel(label)
        //         })
        //     }
        // }
    }

    @Synchronized
    private fun dismissProgressDialog() {
        _eventHandler.postValue(UIEvent(EventType.DISMISS_PATCH_COMM_DIALOG))
        // try {
        //     mProgressDialog?.dismiss()
        //     mProgressDialog = null
        //     navigator?.dismissProgressDialog()
        // } catch (e: IllegalStateException) { }
    }

    fun changePatch() {
        _eventHandler.postValue(UIEvent(EventType.SHOW_CHANGE_PATCH_DIALOG))
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
                    _eventHandler.postValue(UIEvent(EventType.FINISH_ACTIVITY))
                }
            }
        } else {
            mOnCommCheckSuccessListener = {
                deactivate(true) {
                    moveStep((PatchStep.DISCARDED_FOR_CHANGE))
                }
            }
            showPatchCommCheckDialog(true)
            Single.timer(10, TimeUnit.SECONDS)
                .doFinally { dismissPatchCommCheckDialog() }
                .subscribe()
        }
    }

    fun discardPatch() {
        updateIncompletePatchActivationReminder()
        discardPatchInternal()
    }

    private fun discardPatchInternal() {
        val isBolusActive = preferenceManager.patchState.isBolusActive

        if (patchStep.value == PatchStep.SAFE_DEACTIVATION && !isBolusActive) {
            deactivate(true) {
                dismissPatchCommCheckDialogInternal()
                moveStep(PatchStep.MANUALLY_TURNING_OFF_ALARM)
            }

            return
        }

        _eventHandler.postValue(UIEvent(EventType.SHOW_DISCARD_DIALOG))
    }

    fun onConfirm() {
        when (patchStep.value) {
            PatchStep.DISCARDED_FOR_CHANGE       -> PatchStep.WAKE_UP
            PatchStep.DISCARDED_FROM_ALARM       -> PatchStep.FINISH

            PatchStep.DISCARDED                  -> {
                if (initPatchStepIsCheckConnection) {
                    mOnCommCheckDiscardListener?.invoke() /*?: navigator?.finish()*/
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
        }?.let {
            moveStep(it)
        }
    }

    fun initPatchStep() {
        when (patchStep.value) {
            PatchStep.WAKE_UP                            -> {
                setupStep.value = WAKE_UP_READY
            }

            PatchStep.SAFETY_CHECK                       -> {
                setupStep.value = SAFETY_CHECK_READY
            }

            PatchStep.ROTATE_KNOB,
            PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR -> {
                setupStep.value = NEEDLE_SENSING_READY
            }

            else                                         -> Unit
        }
    }

    fun moveStep(newPatchStep: PatchStep) {
        val oldPatchStep = patchStep.value

        if (oldPatchStep != newPatchStep) {
            when (newPatchStep) {
                PatchStep.REMOVE_NEEDLE_CAP      -> PatchLifecycleEvent.createRemoveNeedleCap()
                PatchStep.REMOVE_PROTECTION_TAPE -> PatchLifecycleEvent.createRemoveProtectionTape()
                PatchStep.SAFETY_CHECK           -> PatchLifecycleEvent.createSafetyCheck()
                PatchStep.ROTATE_KNOB            -> PatchLifecycleEvent.createRotateKnob()

                PatchStep.WAKE_UP                -> {
                    patchConfig.apply {
                        rotateKnobNeedleSensingError = false
                    }
                    PatchLifecycleEvent.createShutdown()
                }

                PatchStep.CANCEL                 -> {
                    if (!patchConfig.isActivated) {
                        PatchLifecycleEvent.createShutdown()
                    } else {
                        null
                    }
                }

                else                             -> null
            }?.let {
                preferenceManager.updatePatchLifeCycle(it)
            }
        }

        prepareStep(newPatchStep)

        aapsLogger.info(LTag.PUMP, "moveStep: $oldPatchStep -> $newPatchStep")
    }

    fun initializePatchStep(step: PatchStep?, withAlarmHandle: Boolean = true) {
        mInitPatchStep = prepareStep(step, withAlarmHandle)
        dismissPatchCommCheckDialogInternal(false)
        // dismissTextDialog()
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
                PatchStep.REMOVE_NEEDLE_CAP,
                PatchStep.REMOVE_PROTECTION_TAPE,
                PatchStep.SAFETY_CHECK,
                PatchStep.ROTATE_KNOB,
                PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR,
                PatchStep.BASAL_SCHEDULE             -> R.string.string_activate_patch

                PatchStep.SETTING_REMINDER_TIME      -> R.string.patch_expiration_reminder_setting_title
                else                                 -> _title.value
            }.let {
                if (_title.value != it) {
                    _title.postValue(it)
                }
            }

            patchStep.postValue(newStep)

            if (withAlarmHandle) {
                /* Alarm reset */
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
                        patchConfig.apply {
                            rotateKnobNeedleSensingError = true
                        }

                        updateIncompletePatchActivationReminder(true)

                    }

                    PatchStep.CANCEL                                                                -> {
                        alarmRegistry.remove(AlarmCode.B012).subscribe()
                    }

                    else                                                                            -> {
                    }
                }
            }

            return newStep
        }
    }

    private fun convertToPatchStep(lifecycle: PatchLifecycle) = when (lifecycle) {
        PatchLifecycle.SHUTDOWN               -> patchConfig.isDeactivated.takeOne(
            PatchStep.WAKE_UP, PatchStep.SAFE_DEACTIVATION
        )

        PatchLifecycle.BONDED                 -> PatchStep.CONNECT_NEW
        PatchLifecycle.REMOVE_NEEDLE_CAP      -> PatchStep.REMOVE_NEEDLE_CAP
        PatchLifecycle.REMOVE_PROTECTION_TAPE -> PatchStep.REMOVE_PROTECTION_TAPE
        PatchLifecycle.SAFETY_CHECK           -> PatchStep.SAFETY_CHECK
        PatchLifecycle.ROTATE_KNOB            -> PatchStep.ROTATE_KNOB
        PatchLifecycle.BASAL_SETTING          -> PatchStep.ROTATE_KNOB
        PatchLifecycle.ACTIVATED              -> PatchStep.SAFE_DEACTIVATION
    }

    private fun onClear() {
        mOnCommCheckSuccessListener = null
        mOnCommCheckCancelListener = null
        mOnCommCheckDiscardListener = null
        CommonUtils.dispose(mCommCheckDisposable)
        CommonUtils.dispose(mUpdateDisposable)
        CommonUtils.dispose(mB012UpdateDisposable)
    }

    override fun onCleared() {
        super.onCleared()
        onClear()
    }

    enum class SetupStep {
        WAKE_UP_READY,
        SCAN_STARTED,
        SCAN_FAILED,
        BONDING_STARTED,
        BONDING_FAILED,
        GET_PATCH_INFO_STARTED,
        GET_PATCH_INFO_FAILED,
        SELF_TEST_STARTED,
        SELF_TEST_FAILED,
        SAFETY_CHECK_READY,
        SAFETY_CHECK_STARTED,
        SAFETY_CHECK_FAILED,
        NEEDLE_SENSING_READY,
        NEEDLE_SENSING_STARTED,
        NEEDLE_SENSING_FAILED,
        ACTIVATION_STARTED,
        ACTIVATION_FAILED
    }

    // 셋업 단계, UI 변경이 아닌 BLE 로직을 위한 SetupStep
    val setupStep = MutableLiveData<SetupStep>()

    private fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${setupStep.value}, newSetupStep: $newSetupStep")
        setupStep.postValue(newSetupStep)
    }

    @Synchronized
    fun deactivate(force: Boolean, onSuccessListener: () -> Unit) {
        patchManagerExecutor.deactivate(6000, force)
            .doOnSubscribe {
                showProgressDialog(force.takeOne(R.string.string_in_progress, R.string.string_changing))
            }
            .doFinally {
                dismissProgressDialog()
            }
            .subscribeDefault(aapsLogger) { status ->
                if (status.isDeactivated) {
                    onSuccessListener.invoke()
                } else {
                    rxAction.runOnMainThread({
                                                 checkCommunication({ deactivate(false, onSuccessListener) },
                                                                    { _eventHandler.postValue(UIEvent(EventType.FINISH_ACTIVITY)) })
                                             }, 100)
                }
            }
            .addTo()
    }

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
        patchManager.scan(5000)
            .flatMap {
                if (it.nearestDevice == null)
                    Single.error(Resources.NotFoundException())
                else
                    Single.just(it.nearestDevice)
            }
            .onErrorReturnItem("")
            .doOnSubscribe { updateSetupStep(SCAN_STARTED) }
            .subscribeDefault(aapsLogger) {
                if (it.isNotEmpty()) {
                    startBond(it)
                } else {
                    updateSetupStep(SCAN_FAILED)
                }
            }.addTo()
    }

    @Synchronized
    private fun startBond(scannedMacAddress: String) {
        aapsLogger.info(LTag.PUMP, "startBond: $scannedMacAddress")

        patchManagerExecutor.startBond(scannedMacAddress)
            .doOnSubscribe { updateSetupStep(BONDING_STARTED) }
            .filter { result -> result }
            .toSingle() // 실패시 에러 반환.
            .doOnSuccess { preferenceManager.updatePatchLifeCycle(PatchLifecycleEvent.createBonded()) }
            .doOnError {
                if (it is TimeoutException) {
                    moveStep(PatchStep.WAKE_UP)
                } else {
                    updateSetupStep(BONDING_FAILED)
                }
            }
            .subscribeDefault(aapsLogger) {
                if (it) {
                    getPatchInfo()
                } else {
                    updateSetupStep(BONDING_FAILED)
                }
            }.addTo()
    }

    @Synchronized
    fun getPatchInfo(timeout: Long = 60000) {
        patchManagerExecutor.getPatchInfo(timeout)
            .doOnSubscribe { updateSetupStep(GET_PATCH_INFO_STARTED) }
            .onErrorReturnItem(false)
            .subscribeDefault(aapsLogger) {
                if (it) {
                    selfTest(delayMs = 1000)
                } else {
                    updateSetupStep(GET_PATCH_INFO_FAILED)
                }
            }.addTo()
    }

    @Synchronized
    fun selfTest(timeout: Long = 20000, delayMs: Long = 0) {
        rxAction.runOnMainThread({
                                     patchManagerExecutor.selfTest(timeout)
                                         .doOnSubscribe { updateSetupStep(SELF_TEST_STARTED) }
                                         .map { it == TEST_SUCCESS }
                                         .onErrorReturnItem(false)
                                         .subscribeDefault(aapsLogger) {
                                             if (it) {
                                                 moveStep(PatchStep.REMOVE_NEEDLE_CAP)
                                             } else if (!patchManagerExecutor.patchConnectionState.isConnected) {
                                                 updateSetupStep(SELF_TEST_FAILED)
                                             }
                                         }.addTo()
                                 }, delayMs)
    }

    @Synchronized
    fun retrySafetyCheck() {
        if (mRetryCount <= mMaxRetryCount) {
            startSafetyCheckInternal()
        } else {
            moveStep(PatchStep.REMOVE_NEEDLE_CAP)
        }
    }

    @Synchronized
    fun startSafetyCheck() {
        mRetryCount = 0

        startSafetyCheckInternal()
    }

    private fun startSafetyCheckInternal() {
        patchManagerExecutor.startPriming(10000, 100)
            .doOnSubscribe {
                _safetyCheckProgress.postValue(0)
                updateSetupStep(SAFETY_CHECK_STARTED)
            }
            .doOnNext { _safetyCheckProgress.postValue(it.toInt()) }
            .doOnError { updateSetupStep(SAFETY_CHECK_FAILED) }
            .doOnComplete { moveStep(PatchStep.ROTATE_KNOB) }
            .subscribeEmpty()
            .addTo()
    }

    @Synchronized
    fun startNeedleSensing() {
        patchManagerExecutor.checkNeedleSensing(20000)
            .toObservable()
            .debounce(500, TimeUnit.MILLISECONDS)
            .doOnSubscribe {
                showProgressDialog(R.string.string_connecting)
                updateSetupStep(NEEDLE_SENSING_STARTED)
            }
            .onErrorReturnItem(false)
            .subscribeDefault(aapsLogger) {
                if (it) {
                    startActivation()
                } else {
                    if (!patchManagerExecutor.patchConnectionState.isConnected) {
                        updateSetupStep(NEEDLE_SENSING_FAILED)
                    }
                    dismissProgressDialog()
                }
            }.addTo()
    }

    @Synchronized
    fun startActivation() {
        patchManager.patchActivation(20000)
            .doOnSubscribe {
                showProgressDialog(R.string.string_connecting)
                updateSetupStep(ACTIVATION_STARTED)
            }
            .doFinally { dismissProgressDialog() }
            .onErrorReturnItem(false)
            .subscribeDefault(aapsLogger) {
                if (it) {
                    moveStep(PatchStep.COMPLETE)
                } else {
                    updateSetupStep(ACTIVATION_FAILED)
                }
            }.addTo()
    }

    fun updateIncompletePatchActivationReminder(forced: Boolean = false) {
        if (forced || isSubStepRunning) {
            mB012UpdateSubject.onNext(Unit)
        }
    }

    // @Synchronized
    // private fun createTextDialog(): TextDialog {
    //     dismissTextDialog()
    //
    //     return TextDialog().apply {
    //         mCurrentTextDialog = this
    //     }
    // }

    // @Synchronized
    // private fun dismissTextDialog() {
    //     mCurrentTextDialog?.dismiss()
    //     mCurrentTextDialog = null
    // }
}
