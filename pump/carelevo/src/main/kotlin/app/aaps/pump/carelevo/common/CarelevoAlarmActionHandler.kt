package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.coordinator.CarelevoAlarmClearCoordinator
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SINGLE owner of the in-memory active-alarm queue and of the per-cause alarm-clear state machine,
 * shared by BOTH clear surfaces: the Android notification action ([CarelevoAlarmNotifier]) and the
 * in-app full-screen alarm ([app.aaps.pump.carelevo.presentation.viewmodel.CarelevoAlarmViewModel],
 * which delegates here and keeps only sound/UI concerns).
 *
 * Persistence rule: an alarm that has been successfully handled is REMOVED from the persisted
 * store ([CarelevoAlarmInfoUseCase.acknowledgeAlarm]) as well as from [alarmQueue] — otherwise it
 * resurrects on the next cold load or foreground refresh.
 */
@Singleton
class CarelevoAlarmActionHandler @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val commandQueue: CommandQueue,
    private val alarmUseCase: CarelevoAlarmInfoUseCase,
    private val alarmClearCoordinator: CarelevoAlarmClearCoordinator
) {

    // App-lifetime scope (this is a @Singleton): drives the suspend queue-routed alarm-clear ops.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _alarmQueue = MutableStateFlow<List<CarelevoAlarmInfo>>(emptyList())
    val alarmQueue = _alarmQueue.asStateFlow()

    private val _alarmQueueEmptyEvent = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val alarmQueueEmptyEvent = _alarmQueueEmptyEvent.asSharedFlow()

    /**
     * UI requests the state machine cannot execute itself (launch the Bluetooth-enable intent,
     * show a failure message). Collected by the in-app alarm host; a request that fires while no
     * UI is mounted is intentionally lossy (DROP_OLDEST) — the alarm itself stays queued.
     */
    private val _uiRequests = MutableSharedFlow<AlarmEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uiRequests = _uiRequests.asSharedFlow()

    /** The alarm most recently entering the clear flow (used by the UI for status text). */
    var alarmInfo: CarelevoAlarmInfo? = null

    private val compositeDisposable = CompositeDisposable()

    fun observeAlarms() =
        alarmUseCase.observeAlarms()
            .map { it.orElse(emptyList()) }

    fun getAlarmsOnce(): Single<List<CarelevoAlarmInfo>> =
        alarmUseCase.getAlarmsOnce()
            .map { it.orElse(emptyList()) }

    /** (Re)load the persisted active alarms into [alarmQueue], most severe tier first. */
    fun loadActiveAlarms() {
        compositeDisposable += getAlarmsOnce()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { alarms ->
                    val sorted = alarms
                        .filter { !it.isAcknowledged }
                        .sortedWith(compareBy<CarelevoAlarmInfo> { it.alarmType.code }.thenBy { it.createdAt })
                    _alarmQueue.value = sorted
                    if (sorted.isEmpty()) emitQueueEmpty()
                },
                { e -> aapsLogger.error(LTag.PUMPCOMM, "loadActiveAlarms.error", e) }
            )
    }

    fun triggerEvent(event: AlarmEvent) {
        when (event) {
            is AlarmEvent.ClearAlarm -> startAlarmClearProcess(event.info)
            else                     -> Unit
        }
    }

    /**
     * The per-cause dispatch for a user-confirmed alarm. Branch groups covering the full union of
     * [AlarmCause]:
     * - resume-timeout → clear on patch, ack, resume delivery
     * - clearable ALERT/NOTICE causes → clear on patch, ack; on failure fall back to local ack
     * - WARNING causes (patch fault) → discard the patch (graceful when reachable, local force-quit
     *   when not) — the patch is being abandoned
     * - auto-off WARNING → clear + resume (delivery was only paused)
     * - Bluetooth-off ALERT → local ack + request BT enable + reconnect
     * - informational LGS/BG/timezone notices → no patch op
     */
    private fun startAlarmClearProcess(info: CarelevoAlarmInfo) {
        alarmInfo = info
        val alarmType = info.alarmType
        val alarmCause = info.cause

        aapsLogger.debug(LTag.PUMPCOMM, "startAlarmClearProcess alarmType=$alarmType, alarmCause=$alarmCause")

        // The BLE ops go through the CommandQueue (it reconnects a resting pump first); an
        // unreachable patch is handled by the on-failure fallback path rather than a pre-gate.
        when (alarmCause) {
            AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT,
            AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF                  -> {
                scope.launch {
                    // Resume only if the alarm was actually cleared on the patch — do not resume delivery
                    // into an unresolved alarm condition.
                    if (alarmClearCoordinator.clearAlarmOnPatch(info)) {
                        acknowledgeAndRemoveAlarm(info.alarmId)
                        alarmClearCoordinator.resumeInfusion()
                    } else {
                        _uiRequests.emit(AlarmEvent.ShowToastMessage(R.string.alarm_feat_msg_check_patch_connect))
                    }
                }
            }

            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
            AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
            AlarmCause.ALARM_ALERT_APP_NO_USE,
            AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE,
            AlarmCause.ALARM_ALERT_LOW_BATTERY,
            AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE,
            AlarmCause.ALARM_NOTICE_LOW_INSULIN,
            AlarmCause.ALARM_NOTICE_PATCH_EXPIRED,
            AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK                      -> {
                scope.launch {
                    if (alarmClearCoordinator.clearAlarmOnPatch(info)) acknowledgeAndRemoveAlarm(info.alarmId)
                    else startAlarmAlertAbnormalClearProcess(info, alarmCause)
                }
            }

            AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_ALERT_BLUETOOTH_OFF                            -> {
                startAlarmAlertAbnormalClearProcess(info, alarmCause)
            }

            AlarmCause.ALARM_WARNING_LOW_INSULIN,
            AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
            AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
            AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
            AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED,
            AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
            AlarmCause.ALARM_WARNING_PATCH_ERROR,
            AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
            AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
            AlarmCause.ALARM_WARNING_LOW_BATTERY                            -> {
                discardPatchForAlarm(info)
            }

            AlarmCause.ALARM_NOTICE_BG_CHECK,
            AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED,
            AlarmCause.ALARM_NOTICE_LGS_START,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN,
            AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING                         -> {
                // Informational — no patch-side state to clear; remove locally on confirm.
                acknowledgeAndRemoveAlarm(info.alarmId)
            }

            AlarmCause.ALARM_UNKNOWN                                        -> {
                if (alarmType == AlarmType.WARNING) discardPatchForAlarm(info)
            }
        }
    }

    /** WARNING-tier handling: the patch is faulty and being abandoned. */
    private fun discardPatchForAlarm(info: CarelevoAlarmInfo) {
        if (alarmClearCoordinator.isPatchReachable()) {
            // Reachable: tell the patch to discard (via the queue), then abandon it locally.
            scope.launch {
                alarmClearCoordinator.discardOnAlarm(info)
                startAlarmClearPatchForceQuitProcess()
            }
        } else {
            // Unreachable/faulty patch: abandon locally NOW — do not wait on the queue's
            // connect-loop (up to ~119s) while a critical warning keeps sounding.
            startAlarmClearPatchForceQuitProcess()
        }
    }

    /**
     * The patch could not be reached (or the link itself is the alarm): acknowledge locally so the
     * user is not held hostage by an unclearable alarm, then run any cause-specific recovery.
     */
    private fun startAlarmAlertAbnormalClearProcess(info: CarelevoAlarmInfo, alarmCause: AlarmCause) {
        compositeDisposable += alarmUseCase.acknowledgeAlarm(info.alarmId)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    when (alarmCause) {
                        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> {
                            // Keep the queue entry visible until the link is confirmed back;
                            // ask the UI to fire the system Bluetooth-enable intent.
                            startReconnect(info.alarmId)
                            scope.launch { _uiRequests.emit(AlarmEvent.RequestBluetoothEnable) }
                        }

                        else                                 -> removeFromQueue(info.alarmId)
                    }
                },
                { error -> aapsLogger.error(LTag.PUMPCOMM, "abnormalClear.acknowledge failed cause=$alarmCause", error) }
            )
    }

    private fun startReconnect(alarmId: String) {
        // Reconnect + refresh through the queue (managed connect-before-execute) rather than a raw BLE
        // connect that would fight the queue-owned lifecycle. Ack the alarm once the link is confirmed.
        scope.launch {
            if (commandQueue.readStatus("Bluetooth re-enabled after alarm").success) {
                aapsLogger.debug(LTag.PUMPCOMM, "reconnect readStatus success")
                acknowledgeAndRemoveAlarm(alarmId)
            } else {
                aapsLogger.debug(LTag.PUMPCOMM, "reconnect readStatus failed")
            }
        }
    }

    private fun startAlarmClearPatchForceQuitProcess() {
        alarmClearCoordinator.forceQuitTeardown { clearAllAlarms() }
    }

    /**
     * Handled alarm: remove from the persisted store AND from the in-memory queue. Without the
     * store removal a cleared alarm resurrects on the next cold load / foreground refresh.
     */
    fun acknowledgeAndRemoveAlarm(alarmId: String) {
        compositeDisposable += alarmUseCase.acknowledgeAlarm(alarmId)
            .subscribeOn(aapsSchedulers.io)
            .subscribe(
                { removeFromQueue(alarmId) },
                { e ->
                    aapsLogger.error(LTag.PUMPCOMM, "acknowledgeAndRemoveAlarm.persist failed id=$alarmId", e)
                    // Still drop it from the visible queue — the user confirmed it; worst case it
                    // reappears on the next refresh instead of being stuck on screen forever.
                    removeFromQueue(alarmId)
                }
            )
    }

    private fun removeFromQueue(alarmId: String) {
        _alarmQueue.value = _alarmQueue.value.filterNot { it.alarmId == alarmId }
        if (_alarmQueue.value.isEmpty()) emitQueueEmpty()
    }

    fun clearAllAlarms() {
        compositeDisposable += alarmUseCase.clearAlarms()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    _alarmQueue.value = emptyList()
                    emitQueueEmpty()
                },
                { e ->
                    aapsLogger.error(LTag.PUMPCOMM, "clearAllAlarms.error error=$e")
                })
    }

    private fun emitQueueEmpty() {
        val ok = _alarmQueueEmptyEvent.tryEmit(Unit)
        aapsLogger.debug(LTag.PUMPCOMM, "alarmQueue empty emit=$ok")
    }
}
