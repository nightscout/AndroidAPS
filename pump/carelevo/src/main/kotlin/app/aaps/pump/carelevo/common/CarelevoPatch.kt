package app.aaps.pump.carelevo.common

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.ble.UnsolicitedMessage
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotResponse
import app.aaps.pump.carelevo.ble.commands.InfusionInfoCommand
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.isAvailable
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.codeToInfusionModeCommand
import app.aaps.pump.carelevo.domain.model.bt.InfusionModeResult.Companion.commandToCode
import app.aaps.pump.carelevo.domain.model.bt.PumpStateResult.Companion.codeToPumpStateCommand
import app.aaps.pump.carelevo.domain.model.bt.PumpStateResult.Companion.commandToCode
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpStopUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.min
import org.joda.time.DateTime

class CarelevoPatch @Inject constructor(
    private val transport: CarelevoBleTransport,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val sp: SP,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val infusionInfoMonitorUseCase: CarelevoInfusionInfoMonitorUseCase,
    private val patchInfoMonitorUseCase: CarelevoPatchInfoMonitorUseCase,
    private val userSettingInfoMonitorUseCase: CarelevoUserSettingInfoMonitorUseCase,
    private val patchRptInfusionInfoProcessUseCase: CarelevoPatchRptInfusionInfoProcessUseCase,
    private val createUserSettingInfoUseCase: CarelevoCreateUserSettingInfoUseCase,
    private val carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase,
    private val pumpResumeUseCase: CarelevoPumpResumeUseCase,
    private val pumpStopUseCase: CarelevoPumpStopUseCase,
    private val activeAlarmSnapshotAlarmMapper: ActiveAlarmSnapshotAlarmMapper
) {

    private val bleDisposable = CompositeDisposable()

    private val infoDisposable = CompositeDisposable()

    private var _isWorking = false
    val isWorking get() = _isWorking

    private val _btState: BehaviorSubject<Optional<BleState>> = BehaviorSubject.create()
    val btState get() = _btState

    private val _patchState: BehaviorSubject<Optional<PatchState>> = BehaviorSubject.create()
    val patchState get() = _patchState

    private val _patchInfo: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>> = BehaviorSubject.create()
    val patchInfo get() = _patchInfo

    private val _infusionInfo: BehaviorSubject<Optional<CarelevoInfusionInfoDomainModel>> = BehaviorSubject.create()
    val infusionInfo get() = _infusionInfo

    private val _userSettingInfo: BehaviorSubject<Optional<CarelevoUserSettingInfoDomainModel>> = BehaviorSubject.create()
    val userSettingInfo get() = _userSettingInfo

    private val _profile: BehaviorSubject<Optional<Profile>> = BehaviorSubject.create()
    val profile get() = _profile

    /**
     * Site placement chosen during the activation wizard's site-location step. Read by the needle
     * insertion step when it records the CANNULA_CHANGE therapy event. Defaults to NONE (site
     * rotation disabled or step skipped).
     */
    @Volatile var sitePlacementLocation: TE.Location = TE.Location.NONE
        private set
    @Volatile var sitePlacementArrow: TE.Arrow = TE.Arrow.NONE
        private set

    fun setSitePlacement(location: TE.Location, arrow: TE.Arrow) {
        sitePlacementLocation = location
        sitePlacementArrow = arrow
    }

    private var lastBtState: BleState? = null

    fun initPatch() {
        if (!isWorking) {
            observePatchInfo()
            observeChangeState()
            observeInfusionInfo()
            observeUserSettingInfo()
            _isWorking = true
        }
    }

    fun initPatchAndAwait(): Completable =
        Completable.defer {
            initPatch()
            patchState
                .filter { state ->
                    state == PatchState.NotConnectedNotBooting || state == PatchState.ConnectedBooted
                }
                .firstOrError()
                .ignoreElement()
        }

    /** One-shot wrapper that shares the same init progress across duplicate calls. */
    @Volatile private var inFlightInit: Completable? = null
    fun initPatchOnce(): Completable = synchronized(this) {
        inFlightInit?.let { return it }
        val c = initPatchAndAwait()
            .timeout(20, TimeUnit.SECONDS)
            .cache()
            .doFinally { synchronized(this) { inFlightInit = null } }
        inFlightInit = c
        c
    }

    fun getPatchInfoAddress(): String? {
        return patchInfo.value?.getOrNull()?.address
    }

    /**
     * Per-op sessions leave no resting link to observe, so "connected" collapses to patch validity:
     * an activated patch with Bluetooth available is operational ([PatchState.ConnectedBooted] — every
     * op dials its own session on demand), an activated patch with Bluetooth off is
     * [PatchState.NotConnectedBooted], and no patch is [PatchState.NotConnectedNotBooting].
     */
    fun resolvePatchState(): PatchState {
        val isPatchValid = patchInfo.value?.getOrNull() != null
        val btAvailable = btState.value?.getOrNull()?.isAvailable() ?: false

        return when {
            !isPatchValid -> PatchState.NotConnectedNotBooting
            btAvailable   -> PatchState.ConnectedBooted
            else          -> PatchState.NotConnectedBooted
        }
    }

    private fun observeChangeState() {
        aapsLogger.debug(LTag.PUMPCOMM, "observeChangeState called")
        bleDisposable += BehaviorSubject.combineLatest(
            btState,
            patchInfo
        ) { _, _ ->
            val result = resolvePatchState()
            aapsLogger.debug(LTag.PUMPCOMM, "result : $result")

            _patchState.onNext(Optional.ofNullable(result))

            when (result) {
                is PatchState.NotConnectedNotBooting -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is no connection")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                    rxBus.send(EventRefreshOverview("Carelevo connection state", true))
                    rxBus.send(EventCustomActionsChanged())
                }

                is PatchState.ConnectedBooted        -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is ConnectedBooted")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                    rxBus.send(EventRefreshOverview("Carelevo connection state", true))
                    rxBus.send(EventCustomActionsChanged())
                }

                is PatchState.NotConnectedBooted     -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is NotConnectedBooted")
                }

                else                                 -> {
                    aapsLogger.debug(LTag.PUMPCOMM, "patch state is disconnected")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                }
            }

            result
        }
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnComplete {
                aapsLogger.debug(LTag.PUMPCOMM, "doOnComplete called")
            }
            .doOnError {
                it.printStackTrace()
                aapsLogger.debug(LTag.PUMPCOMM, "doOnError called : $it")
            }
            .subscribe {
                aapsLogger.debug(LTag.PUMPCOMM, "result : $it")
            }
    }

    fun isBluetoothEnabled(): Boolean {
        return btState.value?.getOrNull()?.let {
            it.isEnabled == DeviceModuleState.DEVICE_STATE_ON
        } ?: false
    }

    //===================================================================================================
    fun setProfile(profile: Profile?) {
        _profile.onNext(Optional.ofNullable(profile))
    }

    fun checkIsSameProfile(newProfile: Profile?): Boolean {
        val setProfile = profile.value?.getOrNull() ?: return false
        val a = newProfile ?: return false
        val aVals = a.getBasalValues()
        val bVals = setProfile.getBasalValues()

        if (aVals.size != bVals.size) return false

        for (i in aVals.indices) {
            if (TimeUnit.SECONDS.toMinutes(aVals[i].timeAsSeconds.toLong()) !=
                TimeUnit.SECONDS.toMinutes(bVals[i].timeAsSeconds.toLong())
            ) return false

            if (!nearlyEqual(aVals[i].value.toFloat(), bVals[i].value.toFloat())) return false
        }
        return true
    }

    private fun nearlyEqual(a: Float, b: Float, epsilon: Float = 1e-3f): Boolean {
        val absA = abs(a)
        val absB = abs(b)
        val diff = abs(a - b)
        return if (a == b) {
            true
        } else if (a == 0f || b == 0f || absA + absB < java.lang.Float.MIN_NORMAL) {
            diff < epsilon * java.lang.Float.MIN_NORMAL
        } else {
            diff / min(absA + absB, Float.MAX_VALUE) < epsilon
        }
    }

    /**
     * btState source: the plugin's Bluetooth broadcast receiver (adapter on/off) plus its startup
     * seed. Raises the Bluetooth-off alarm on the ON→OFF edge.
     */
    fun onBluetoothStateChanged(state: BleState) {
        aapsLogger.debug(LTag.PUMPCOMM, "btState : $state")
        if (state.isEnabled == DeviceModuleState.DEVICE_STATE_OFF &&
            lastBtState != null && lastBtState?.isEnabled != DeviceModuleState.DEVICE_STATE_OFF
        ) {
            handleAlarm("alert", value = null, cause = AlarmCause.ALARM_ALERT_BLUETOOTH_OFF)
        }
        lastBtState = state
        _btState.onNext(Optional.of(state))
    }

    fun releasePatch() {
        flushPatchInformation()
    }

    fun flushPatchInformation() {
        // Clear cached patch/infusion info. This resets isCheckScreen (which otherwise keeps the
        // wizard latched to SAFETY_CHECK after a mid-activation discard) and immediately drops
        // patchState to NotConnectedNotBooting. There is no resting GATT to tear down — sessions are per-op.
        _patchInfo.onNext(Optional.empty())
        _infusionInfo.onNext(Optional.empty())
    }

    private val discardInProgress = AtomicBoolean(false)

    /**
     * Full BLE teardown for a discarded patch: remove the OS bond, then [releasePatch] (clear cached
     * patch info). Single-flight — if a teardown is already running (e.g. a queued CmdDiscard racing
     * the ViewModel force-discard fallback), the second caller is skipped. Self-contained: swallows
     * teardown errors, so callers report the already-decided discard result unaffected.
     */
    fun discardTeardown() {
        if (!discardInProgress.compareAndSet(false, true)) {
            aapsLogger.debug(LTag.PUMPCOMM, "discardTeardown skipped (already in progress)")
            return
        }
        // Unbond and flush independently: a failed unbond (adapter gone, bond already removed)
        // must never leave the discarded patch's cached info behind — a stale record would keep
        // patchState at ConnectedBooted and re-latch the wizard's check screens.
        try {
            getPatchInfoAddress()?.let { transport.adapter.removeBond(it.uppercase()) }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "discardTeardown unbond error", e)
        }
        try {
            releasePatch()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "discardTeardown release error", e)
        } finally {
            discardInProgress.set(false)
        }
    }

    /**
     * Persist an infusion-info report decoded by the [app.aaps.pump.carelevo.ble.BleClient] stack.
     * [pumpStateRaw]/[modeRaw] are the raw 0x91 bytes; they are normalized through the
     * `codeTo…().commandToCode()` round-trip the persisted codes require, so the persisted values
     * are byte-for-byte identical to a full decode.
     */
    fun applyInfusionInfoReport(
        runningMinutes: Int,
        remains: Double,
        infusedTotalBasalAmount: Double,
        infusedTotalBolusAmount: Double,
        pumpStateRaw: Int,
        modeRaw: Int
    ) {
        dispatchInfusionInfo(
            CarelevoPatchRptInfusionInfoRequestModel(
                runningMinute = runningMinutes,
                remains = remains,
                infusedTotalBasalAmount = infusedTotalBasalAmount,
                infusedTotalBolusAmount = infusedTotalBolusAmount,
                pumpState = pumpStateRaw.codeToPumpStateCommand().commandToCode(),
                mode = modeRaw.codeToInfusionModeCommand().commandToCode(),
                currentInfusedProgramVolume = 0.0, // not persisted by the process use case
                realInfusedTime = 0
            )
        )
    }

    private fun dispatchInfusionInfo(requestModel: CarelevoPatchRptInfusionInfoRequestModel) {
        bleDisposable += patchRptInfusionInfoProcessUseCase.execute(requestModel)
            .timeout(3, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe()
    }

    /**
     * Raise a patch alarm locally (alarm DB + notifier pipeline). Public seam: the long-lived
     * `unsolicitedEvents` bridge feeds pump-pushed alert/warning/notice frames here; today it is
     * used for the Bluetooth-off alert.
     */
    fun handleAlarm(modelType: String, value: Int?, cause: AlarmCause) {
        aapsLogger.debug(LTag.PUMPCOMM, "$modelType report : $value, $cause")
        val info = CarelevoAlarmInfo(
            // UUID, not a millisecond timestamp — two alarms raised in the same millisecond (rapid
            // BLE flapping) would otherwise collide and one would silently overwrite the other.
            alarmId = UUID.randomUUID().toString(),
            alarmType = cause.alarmType,
            cause = cause,
            value = value,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString(),
            isAcknowledged = false
        )
        bleDisposable += carelevoAlarmInfoUseCase.upsertAlarm(info)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { aapsLogger.debug(LTag.PUMPCOMM, "handleAlarm upsert complete") },
                { e -> aapsLogger.error(LTag.PUMPCOMM, "handleAlarm upsert error", e) }
            )
    }

    /**
     * Reconcile the locally persisted active alarms against the reconnect snapshot returned by
     * 0x43 → 0xA4/0xA5/0xA6. Duplicate conditions across tiers are de-duped with priority A4 > A5 > A6.
     *
     * A snapshot is a level poll ("is this condition true right now"), not an event — the patch keeps
     * reporting e.g. `OUT_OF_INSULIN` as long as insulin is genuinely low, with no per-event id to tell
     * "still the same alarm" apart from "a new one". Treating every poll's alarms as the full active
     * set (as a blind replace would) means a cause the user just acknowledged (removed from the store
     * by [app.aaps.pump.carelevo.common.CarelevoAlarmActionHandler.acknowledgeAndRemoveAlarm]) gets
     * silently reinserted by the very next reconnect, because the underlying condition hasn't cleared —
     * "확인" never sticks. So this only [CarelevoAlarmInfoUseCase.upsertAlarm]s a cause that is newly
     * active compared to the PREVIOUS snapshot (an edge, off→on) — the same semantics the pushed
     * 0xA1/0xA2/0xA3 reports already have, since the patch only pushes those on a real transition.
     * Symmetrically, a cause the previous snapshot had and this one doesn't is confirmed resolved on
     * the patch, so it is dropped from the active store even if the user never acknowledged it.
     */
    fun applyActiveAlarmSnapshots(snapshots: List<ActiveAlarmSnapshotResponse>) {
        if (snapshots.isEmpty()) return

        reconcileInfusingStateFromSnapshot(snapshots.first().infusing)

        val now = LocalDateTime.now().toString()
        val currentAlarms = activeAlarmSnapshotAlarmMapper.map(
            snapshots = snapshots,
            currentPatch = patchInfo.value?.getOrNull(),
            now = now
        )
        val currentCauses = currentAlarms.map { it.cause }.toSet()
        val previousCauses = loadLastSnapshotAlarmCauses()

        val newlyRaised = currentAlarms.filter { it.cause !in previousCauses }
        val resolvedCauses = previousCauses - currentCauses

        val upserts = newlyRaised.map { carelevoAlarmInfoUseCase.upsertAlarm(it) }
        val resolve = if (resolvedCauses.isEmpty()) {
            Completable.complete()
        } else {
            carelevoAlarmInfoUseCase.getAlarmsOnce()
                .map { it.orElse(emptyList()).filter { alarm -> alarm.cause in resolvedCauses } }
                .flatMapCompletable { resolved ->
                    if (resolved.isEmpty()) Completable.complete()
                    else Completable.merge(resolved.map { carelevoAlarmInfoUseCase.acknowledgeAlarm(it.alarmId) })
                }
        }

        bleDisposable += Completable.merge(upserts + resolve)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    // Advance the edge-detection baseline only once the writes it represents actually
                    // landed — saving it eagerly (before upserts/resolves are even subscribed) would let
                    // a failed upsert get silently skipped forever, since the next poll would already
                    // see that cause as "already seen" and never retry it.
                    saveLastSnapshotAlarmCauses(currentCauses)
                    aapsLogger.info(
                        LTag.PUMPCOMM,
                        "applyActiveAlarmSnapshots newlyRaised=${newlyRaised.map { it.cause }} resolved=$resolvedCauses"
                    )
                },
                { e -> aapsLogger.error(LTag.PUMPCOMM, "applyActiveAlarmSnapshots error", e) }
            )
    }

    /** Which causes the last processed snapshot reported active — survives across acknowledges on purpose. */
    private fun loadLastSnapshotAlarmCauses(): Set<AlarmCause> =
        sp.getString(PREF_KEY_LAST_SNAPSHOT_ALARM_CAUSES, "")
            .split(",")
            .mapNotNull { name -> runCatching { AlarmCause.valueOf(name) }.getOrNull() }
            .toSet()

    private fun saveLastSnapshotAlarmCauses(causes: Set<AlarmCause>) {
        sp.putString(PREF_KEY_LAST_SNAPSHOT_ALARM_CAUSES, causes.joinToString(",") { it.name })
    }

    private fun reconcileInfusingStateFromSnapshot(infusing: Boolean) {
        val patch = patchInfo.value?.getOrNull() ?: return
        val shouldBeStopped = !infusing
        if ((patch.isStopped ?: false) == shouldBeStopped) return

        val persisted = if (infusing) {
            pumpResumeUseCase.persistResumed()
        } else {
            pumpStopUseCase.persistStopped(patch.stopMinutes ?: 0)
        }
        aapsLogger.info(LTag.PUMPCOMM, "active alarm snapshot infusing=$infusing persisted=$persisted")
        if (!persisted) return

        val updated = if (infusing) {
            patch.copy(
                updatedAt = DateTime.now(),
                isStopped = false,
                stopMinutes = null,
                stopMode = null,
                isForceStopped = null,
                mode = 1
            )
        } else {
            patch.copy(
                updatedAt = DateTime.now(),
                isStopped = true,
                stopMinutes = patch.stopMinutes ?: 0,
                stopMode = 0,
                isForceStopped = false,
                mode = 0
            )
        }
        _patchInfo.onNext(Optional.of(updated))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        rxBus.send(EventRefreshOverview("Carelevo active alarm snapshot", true))
    }

    /**
     * Reconcile local state to "resumed" after a timed pump-stop ends. The patch auto-resumes at the stop
     * duration, but with per-op sessions there is no live link to receive its stop-report push, and a 0x91
     * status read does not carry the suspend flag (see `CarelevoPatchRptInfusionInfoProcessUseCase`) — so
     * without this, [CarelevoPatchInfoDomainModel.isStopped] would stay latched until an app-initiated
     * resume. Called by the plugin's expiry watchdog and by [onUnsolicited] when a stop/basal-restart
     * report is caught mid-session. Repository writes are synchronous, so callers invoke it off the main
     * thread. Idempotent — a no-op when the pump is already running.
     */
    fun reconcileAutoResumed() {
        // Only act when the pump is currently marked stopped — there is nothing to resume otherwise. This
        // guards against a basal-restart report (0x88) that fires during normal delivery, or that races a
        // stop command: an unconditional persistResumed would clear a legitimate suspend.
        if (patchInfo.value?.getOrNull()?.isStopped != true) {
            aapsLogger.debug(LTag.PUMPCOMM, "auto-resume reconcile skipped: pump not stopped")
            return
        }
        val ok = pumpResumeUseCase.persistResumed()
        aapsLogger.info(LTag.PUMPCOMM, "auto-resume reconcile: persistResumed=$ok")
        if (!ok) return
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        rxBus.send(EventRefreshOverview("Carelevo auto-resume", true))
    }

    /**
     * Route a patch-pushed [UnsolicitedMessage] (a notification that matched no in-flight request) by
     * opcode — the bridge the BLE migration dropped. Stop-report (0x8A) / basal-restart (0x88) reconcile
     * the resumed state; warning/alert/notice pushes (0xA1/0xA2/0xA3) decode to an [AlarmCause] and raise
     * the alarm, reusing the pre-migration mapping. Runs on the session's IO scope and must NOT start a new
     * BLE session (the session mutex is held for the whole call — a nested session self-deadlocks);
     * [reconcileAutoResumed] and [handleAlarm] only persist, so this is safe.
     */
    fun onUnsolicited(msg: UnsolicitedMessage) {
        when (msg.opcode) {
            RPT_INFUSION_INFO                 -> {
                val info = InfusionInfoCommand().decode(msg.payload)
                aapsLogger.info(
                    LTag.PUMPCOMM,
                    "unsolicited infusion info subId=${info.subId} remains=${info.insulinRemaining} " +
                        "basal=${info.infusedTotalBasalAmount} bolus=${info.infusedTotalBolusAmount} " +
                        "pumpState=${info.pumpStateRaw} mode=${info.modeRaw} running=${info.runningMinutes}"
                )
                applyInfusionInfoReport(
                    runningMinutes = info.runningMinutes,
                    remains = info.insulinRemaining,
                    infusedTotalBasalAmount = info.infusedTotalBasalAmount,
                    infusedTotalBolusAmount = info.infusedTotalBolusAmount,
                    pumpStateRaw = info.pumpStateRaw,
                    modeRaw = info.modeRaw
                )
            }

            RPT_PUMP_STOP, RPT_BASAL_RESTART -> {
                aapsLogger.info(LTag.PUMPCOMM, "unsolicited resume report ${hex(msg.opcode)} -> reconcile")
                reconcileAutoResumed()
            }

            RPT_WARNING                      -> raiseAlarm("warning", AlarmType.WARNING, msg.payload)
            RPT_ALERT                        -> raiseAlarm("alert", AlarmType.ALERT, msg.payload)
            RPT_NOTICE                       -> raiseAlarm("notice", AlarmType.NOTICE, msg.payload)

            else                             ->
                aapsLogger.debug(LTag.PUMPCOMM, "unsolicited frame ${hex(msg.opcode)} (${msg.payload.size}B) unhandled")
        }
    }

    /** Decode a pushed alarm frame `[opcode][cause][value]` to an [AlarmCause] and raise it via [handleAlarm]. */
    private fun raiseAlarm(modelType: String, type: AlarmType, payload: ByteArray) {
        val causeCode = payload.getOrNull(1)?.toUByte()?.toInt()
        val value = payload.getOrNull(2)?.toUByte()?.toInt()
        handleAlarm(modelType, value, AlarmCause.fromTypeAndCode(type, causeCode))
    }

    private fun observeInfusionInfo() {
        infoDisposable += infusionInfoMonitorUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoInfusionInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> $result")
                        _infusionInfo.onNext(Optional.ofNullable(result))
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun observePatchInfo() {
        infoDisposable += patchInfoMonitorUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoPatchInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> ${result?.needleFailedCount}")
                        _patchInfo.onNext(Optional.ofNullable(result))
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun observeUserSettingInfo() {
        infoDisposable += userSettingInfoMonitorUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoUserSettingInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> $result")
                        _userSettingInfo.onNext(Optional.ofNullable(result))
                        if (result == null) {
                            createUserSettingInfo()
                        }
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
            }
    }

    private fun createUserSettingInfo() {
        // Read prefs + build the request on IO: createUserSettingInfo() runs from observeUserSettingInfo's
        // Main-thread subscribe, and SharedPreferences reads on the main thread risk an ANR.
        infoDisposable += Single.fromCallable {
            CarelevoUserSettingInfoRequestModel(
                lowInsulinNoticeAmount = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key, 30),
                maxBasalSpeed = 15.0,
                maxBolusDose = preferences.get(DoubleKey.SafetyMaxBolus)
            )
        }
            .subscribeOn(aapsSchedulers.io)
            .flatMap { createUserSettingInfoUseCase.execute(it) }
            .subscribe()
    }

    private companion object {

        // Unsolicited report opcodes (byte 0). Stop/basal-restart are the auto-resume signals; the
        // 0xA1/0xA2/0xA3 message reports are the pushed alarms (severity tier via AlarmType).
        private const val RPT_BASAL_RESTART: Byte = 0x88.toByte() // CMD_BASAL_RESTART_RPT
        private const val RPT_PUMP_STOP: Byte = 0x8A.toByte()     // CMD_PUMP_STOP_RPT (stop window ended)
        private const val RPT_INFUSION_INFO: Byte = 0x91.toByte() // CMD_INFUSION_INFO_RPT (auto/reconnect push)
        private const val RPT_WARNING: Byte = 0xA1.toByte()       // CMD_WARNING_MSG_RPT
        private const val RPT_ALERT: Byte = 0xA2.toByte()         // CMD_ALERT_MSG_RPT
        private const val RPT_NOTICE: Byte = 0xA3.toByte()        // CMD_NOTICE_MSG_RPT

        // Comma-joined AlarmCause names last seen active in an 0x43→0xA4/A5/A6 snapshot — the
        // edge-detection baseline for applyActiveAlarmSnapshots, kept across process restarts so a
        // cold start doesn't treat every still-active condition as newly raised.
        private const val PREF_KEY_LAST_SNAPSHOT_ALARM_CAUSES = "carelevo_last_snapshot_alarm_causes"

        private fun hex(b: Byte) = "0x%02X".format(b.toInt() and 0xFF)
    }
}
