@file:OptIn(ExperimentalUuidApi::class)

package app.aaps.pump.carelevo.common

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.UnsolicitedMessage
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotResponse
import app.aaps.pump.carelevo.ble.commands.InfusionInfoCommand
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.isAvailable
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.di.CarelevoCentral
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
import app.aaps.pump.carelevo.ext.localNowIsoText
import app.aaps.pump.carelevo.ext.toHexText
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalAtomicApi::class)
@SingleIn(AppScope::class)
class CarelevoPatch @Inject constructor(
    @CarelevoCentral private val central: BleCentral,
    private val rxBus: RxBus,
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

    /** Where the patch streams are collected and its storage written. A test sets an unconfined dispatcher. */
    internal var dispatcher: CoroutineDispatcher = aapsIoDispatcher

    // A failure no caller handles is logged, not thrown at the app.
    private val scope: CoroutineScope by lazy {
        CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, e -> aapsLogger.error(LTag.PUMPCOMM, "patch task failed", e) })
    }

    private var _isWorking = false
    val isWorking get() = _isWorking

    // Each stream keeps its last value and has none before the first one: see latestValueFlow.
    private val _btState = latestValueFlow<BleState?>()
    val btState: SharedFlow<BleState?> get() = _btState

    private val _patchState = latestValueFlow<PatchState?>()
    val patchState: SharedFlow<PatchState?> get() = _patchState

    private val _patchInfo = latestValueFlow<CarelevoPatchInfoDomainModel?>()
    val patchInfo: SharedFlow<CarelevoPatchInfoDomainModel?> get() = _patchInfo

    private val _infusionInfo = latestValueFlow<CarelevoInfusionInfoDomainModel?>()
    val infusionInfo: SharedFlow<CarelevoInfusionInfoDomainModel?> get() = _infusionInfo

    private val _userSettingInfo = latestValueFlow<CarelevoUserSettingInfoDomainModel?>()
    val userSettingInfo: SharedFlow<CarelevoUserSettingInfoDomainModel?> get() = _userSettingInfo

    private val _profile = latestValueFlow<Profile?>()
    val profile: SharedFlow<Profile?> get() = _profile

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

    /** Starts the patch streams, then waits until it is known whether a patch is there to use. */
    suspend fun initPatchAndAwait() {
        initPatch()
        patchState.first { it == PatchState.NotConnectedNotBooting || it == PatchState.ConnectedBooted }
    }

    /** One start shared by every caller that comes while it runs. It fails when it does not end in [INIT_TIMEOUT]. */
    @Volatile private var inFlightInit: Deferred<Unit>? = null
    private val initLock = AapsLock()
    fun initPatchOnce(): Deferred<Unit> = initLock.withLock {
        inFlightInit?.let { return it }
        val started = scope.async {
            withTimeoutOrNull(INIT_TIMEOUT) { initPatchAndAwait() } ?: throw IllegalStateException("patch init did not end in $INIT_TIMEOUT")
        }
        inFlightInit = started
        started.invokeOnCompletion { initLock.withLock { if (inFlightInit === started) inFlightInit = null } }
        started
    }

    fun getPatchInfoAddress(): String? {
        return patchInfo.latest?.address
    }

    /**
     * Per-op sessions leave no resting link to observe, so "connected" collapses to patch validity:
     * an activated patch with Bluetooth available is operational ([PatchState.ConnectedBooted] — every
     * op dials its own session on demand), an activated patch with Bluetooth off is
     * [PatchState.NotConnectedBooted], and no patch is [PatchState.NotConnectedNotBooting].
     */
    fun resolvePatchState(): PatchState {
        val isPatchValid = patchInfo.latest != null
        val btAvailable = btState.latest?.isAvailable() ?: false

        return when {
            !isPatchValid -> PatchState.NotConnectedNotBooting
            btAvailable   -> PatchState.ConnectedBooted
            else          -> PatchState.NotConnectedBooted
        }
    }

    private fun observeChangeState() {
        aapsLogger.debug(LTag.PUMPCOMM, "observeChangeState called")
        combine(btState, patchInfo) { _, _ -> resolvePatchState() }.onEach { result ->
            aapsLogger.debug(LTag.PUMPCOMM, "result : $result")

            _patchState.tryEmit(result)

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
        }.launchIn(scope)
    }

    fun isBluetoothEnabled(): Boolean {
        return btState.latest?.let {
            it.isEnabled == DeviceModuleState.DEVICE_STATE_ON
        } ?: false
    }

    //===================================================================================================
    fun setProfile(profile: Profile?) {
        _profile.tryEmit(profile)
    }

    fun checkIsSameProfile(newProfile: Profile?): Boolean {
        val setProfile = profile.latest ?: return false
        val a = newProfile ?: return false
        val aVals = a.getBasalValues()
        val bVals = setProfile.getBasalValues()

        if (aVals.size != bVals.size) return false

        for (i in aVals.indices) {
            if (aVals[i].timeAsSeconds / 60 != bVals[i].timeAsSeconds / 60) return false

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
        } else if (a == 0f || b == 0f || absA + absB < FLOAT_MIN_NORMAL) {
            diff < epsilon * FLOAT_MIN_NORMAL
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
        _btState.tryEmit(state)
    }

    fun releasePatch() {
        flushPatchInformation()
    }

    fun flushPatchInformation() {
        // Clear cached patch/infusion info. This resets isCheckScreen (which otherwise keeps the
        // wizard latched to SAFETY_CHECK after a mid-activation discard) and immediately drops
        // patchState to NotConnectedNotBooting. There is no resting GATT to tear down — sessions are per-op.
        _patchInfo.tryEmit(null)
        _infusionInfo.tryEmit(null)
        // The patch identity this baseline was tracking is gone too — see clearLastSnapshotAlarmCauses.
        clearLastSnapshotAlarmCauses()
    }

    private val discardInProgress = AtomicBoolean(false)

    /**
     * Full BLE teardown for a discarded patch: remove the OS bond, then [releasePatch] (clear cached
     * patch info). Single-flight — if a teardown is already running (e.g. a queued CmdDiscard racing
     * the ViewModel force-discard fallback), the second caller is skipped. Self-contained: swallows
     * teardown errors, so callers report the already-decided discard result unaffected.
     */
    /**
     * Callers that delete the stored patch first must pass the [address] they read before the delete:
     * the in-memory patch info is cleared on another thread and may already be gone here.
     */
    fun discardTeardown(address: String? = getPatchInfoAddress()) {
        if (!discardInProgress.compareAndSet(false, true)) {
            aapsLogger.debug(LTag.PUMPCOMM, "discardTeardown skipped (already in progress)")
            return
        }
        // Unbond and flush independently: a failed unbond (adapter gone, bond already removed)
        // must never leave the discarded patch's cached info behind — a stale record would keep
        // patchState at ConnectedBooted and re-latch the wizard's check screens.
        try {
            address?.let { mac ->
                // The callers are not coroutines yet. Removing a bond does not wait for the system on Android.
                val result = runBlocking { central.bonding?.removeBond(DeviceId(mac.uppercase())) }
                if (result is BleResult.Failed) aapsLogger.warn(LTag.PUMPCOMM, "discardTeardown unbond failed: ${result.failure}")
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "discardTeardown unbond error", e)
        }
        try {
            releasePatch()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "discardTeardown release error", e)
        } finally {
            discardInProgress.store(false)
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
        scope.launch { withTimeoutOrNull(REPORT_WRITE_TIMEOUT) { patchRptInfusionInfoProcessUseCase.execute(requestModel) } }
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
            alarmId = Uuid.random().toString(),
            alarmType = cause.alarmType,
            cause = cause,
            value = value,
            createdAt = localNowIsoText(),
            updatedAt = localNowIsoText(),
            isAcknowledged = false
        )
        scope.launch {
            try {
                carelevoAlarmInfoUseCase.upsertAlarm(info)
                aapsLogger.debug(LTag.PUMPCOMM, "handleAlarm upsert complete")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPCOMM, "handleAlarm upsert error", e)
            }
        }
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

        val now = localNowIsoText()
        val currentAlarms = activeAlarmSnapshotAlarmMapper.map(
            snapshots = snapshots,
            currentPatch = patchInfo.latest,
            now = now
        )
        val currentCauses = currentAlarms.map { it.cause }.toSet()
        val previousCauses = loadLastSnapshotAlarmCauses()

        val newlyRaised = currentAlarms.filter { it.cause !in previousCauses }
        val resolvedCauses = previousCauses - currentCauses

        // One after another, as the merged writes ran before: the new alarms, then the ones the patch no longer reports.
        scope.launch {
            try {
                newlyRaised.forEach { carelevoAlarmInfoUseCase.upsertAlarm(it) }
                if (resolvedCauses.isNotEmpty()) {
                    carelevoAlarmInfoUseCase.getAlarmsOnce()
                        .filter { alarm -> alarm.cause in resolvedCauses }
                        .forEach { carelevoAlarmInfoUseCase.acknowledgeAlarm(it.alarmId) }
                }
                // Advance the edge-detection baseline only once the writes it represents actually
                // landed — saving it eagerly (before upserts/resolves have run) would let
                // a failed upsert get silently skipped forever, since the next poll would already
                // see that cause as "already seen" and never retry it.
                saveLastSnapshotAlarmCauses(currentCauses)
                aapsLogger.info(
                    LTag.PUMPCOMM,
                    "applyActiveAlarmSnapshots newlyRaised=${newlyRaised.map { it.cause }} resolved=$resolvedCauses"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPCOMM, "applyActiveAlarmSnapshots error", e)
            }
        }
    }

    /**
     * Which causes the last processed snapshot reported active — survives across acknowledges on
     * purpose. Stored comma-joined in [CarelevoStringNonKey.LastSnapshotAlarmCauses] so it is kept
     * across process restarts: a cold start must not treat every still-active condition as newly
     * raised.
     */
    private fun loadLastSnapshotAlarmCauses(): Set<AlarmCause> =
        preferences.get(CarelevoStringNonKey.LastSnapshotAlarmCauses)
            .split(",")
            .mapNotNull { name -> runCatching { AlarmCause.valueOf(name) }.getOrNull() }
            .toSet()

    private fun saveLastSnapshotAlarmCauses(causes: Set<AlarmCause>) {
        preferences.put(CarelevoStringNonKey.LastSnapshotAlarmCauses, causes.joinToString(",") { it.name })
    }

    /**
     * The baseline is otherwise global (not keyed by patch address), so without this a cause the
     * *previous* patch already reported would silently suppress that same cause the very first time
     * a freshly-paired patch reports it — [applyActiveAlarmSnapshots] would see it as already-seen
     * and never raise it, even though this patch (and its alarm records) are brand new.
     */
    private fun clearLastSnapshotAlarmCauses() {
        preferences.remove(CarelevoStringNonKey.LastSnapshotAlarmCauses)
    }

    /**
     * An alarm removed locally without a successful AlarmClear is still active on the patch. Drop it
     * from the baseline so the next snapshot treats it as newly raised instead of suppressing it.
     */
    fun forgetSnapshotAlarmCause(cause: AlarmCause) {
        val previous = loadLastSnapshotAlarmCauses()
        if (cause !in previous) return
        saveLastSnapshotAlarmCauses(previous - cause)
        aapsLogger.info(LTag.PUMPCOMM, "snapshot baseline forget cause=$cause (cleared locally only)")
    }

    private fun reconcileInfusingStateFromSnapshot(infusing: Boolean) {
        val patch = patchInfo.latest ?: return
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
                updatedAt = Clock.System.now(),
                isStopped = false,
                stopMinutes = null,
                stopMode = null,
                isForceStopped = null,
                mode = 1
            )
        } else {
            patch.copy(
                updatedAt = Clock.System.now(),
                isStopped = true,
                stopMinutes = patch.stopMinutes ?: 0,
                stopMode = 0,
                isForceStopped = false,
                mode = 0
            )
        }
        _patchInfo.tryEmit(updated)
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
        if (patchInfo.latest?.isStopped != true) {
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
        // The value byte is part of the identity for codes that share one cause code - without it every
        // LGS-finished reason resolved to ALARM_NOTICE_LGS_FINISHED_UNKNOWN.
        handleAlarm(modelType, value, AlarmCause.fromTypeAndCode(type, causeCode, value))
    }

    private fun observeInfusionInfo() {
        scope.launch {
            infusionInfoMonitorUseCase.execute().collect { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoInfusionInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> $result")
                        _infusionInfo.tryEmit(result)
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
    }

    private fun observePatchInfo() {
        scope.launch {
            patchInfoMonitorUseCase.execute().collect { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoPatchInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> ${result?.needleFailedCount}")
                        _patchInfo.tryEmit(result)
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
    }

    private fun observeUserSettingInfo() {
        scope.launch {
            userSettingInfoMonitorUseCase.execute().collect { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        val result = response.data as CarelevoUserSettingInfoDomainModel?
                        aapsLogger.debug(LTag.PUMPCOMM, "response success result ==> $result")
                        _userSettingInfo.tryEmit(result)
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
    }

    private fun createUserSettingInfo() {
        // Read prefs + build the request on the patch dispatcher (IO): SharedPreferences reads on the main thread risk an ANR.
        scope.launch {
            val request = CarelevoUserSettingInfoRequestModel(
                lowInsulinNoticeAmount = preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS),
                maxBasalSpeed = 15.0,
                maxBolusDose = preferences.get(DoubleKey.SafetyMaxBolus)
            )
            createUserSettingInfoUseCase.execute(request)
        }
    }

    private companion object {

        /** How long a start waits to know whether a patch is there. */
        val INIT_TIMEOUT = 20.seconds

        /** How long the store write of a pushed infusion report may take. */
        val REPORT_WRITE_TIMEOUT = 3.seconds

        // Unsolicited report opcodes (byte 0). Stop/basal-restart are the auto-resume signals; the
        // 0xA1/0xA2/0xA3 message reports are the pushed alarms (severity tier via AlarmType).
        private const val RPT_BASAL_RESTART: Byte = 0x88.toByte() // CMD_BASAL_RESTART_RPT
        private const val RPT_PUMP_STOP: Byte = 0x8A.toByte()     // CMD_PUMP_STOP_RPT (stop window ended)
        private const val RPT_INFUSION_INFO: Byte = 0x91.toByte() // CMD_INFUSION_INFO_RPT (auto/reconnect push)
        private const val RPT_WARNING: Byte = 0xA1.toByte()       // CMD_WARNING_MSG_RPT
        private const val RPT_ALERT: Byte = 0xA2.toByte()         // CMD_ALERT_MSG_RPT
        private const val RPT_NOTICE: Byte = 0xA3.toByte()        // CMD_NOTICE_MSG_RPT

        private fun hex(b: Byte) = b.toHexText().uppercase()

        /** The smallest normal float, as `java.lang.Float.MIN_NORMAL`. */
        private val FLOAT_MIN_NORMAL: Float = Float.fromBits(0x00800000)
    }
}
