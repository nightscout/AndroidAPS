package app.aaps.pump.aaruy.services

import app.aaps.pump.aaruy.AaruyStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.alerts.PumpAlert
import app.aaps.core.interfaces.pump.alerts.PumpAlertAction
import app.aaps.core.interfaces.pump.alerts.PumpAlertLifetime
import app.aaps.core.interfaces.pump.alerts.PumpAlertSeverity
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.common.enums.AlarmState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart

/**
 * Every alarm of the pump goes to the user as a pump alert (acceptance A1). The driver plays no sound and posts no alarm
 * card of its own (A2).
 *
 * The alarms the pump holds are [AaruyPump.activeAlarms], and the alerts follow that map: an alarm in it is raised with
 * the time it was first seen, one that left it is resolved. So an app start raises again what is stored, with the same
 * time (A4), and a confirm or another pump resolves what it removes (A6). A command the pump refused is an event: it
 * ends when the user saw it.
 */
@SingleIn(AppScope::class)
@Inject
class AaruyAlerts(
    private val pumpAlerts: PumpAlerts,
    private val commandQueue: CommandQueue,
    private val aaruyPump: AaruyPump,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    /** Until [stop]. UNDISPATCHED, so the alerts of the stored alarms are up before `onStart` goes on. */
    fun start(scope: CoroutineScope) {
        aaruyPump.activeAlarmsFlow.collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { show(it) }
        aaruyPump.pumpWarnings.collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { refused ->
            pumpAlerts.raise(alertFor(refused, raisedAt = dateUtil.now(), lifetime = PumpAlertLifetime.EVENT))
        }
    }

    /** The driver stops: none of its alerts is true any more (A6). */
    fun stop() = pumpAlerts.resolveAll(SOURCE)

    private fun show(held: Map<AlarmState, Long>) {
        val keys = held.keys.map { it.name }.toSet()
        pumpAlerts.active.value
            .filter { it.source == SOURCE && it.lifetime == PumpAlertLifetime.CONDITION && it.key !in keys }
            .forEach { pumpAlerts.resolve(SOURCE, it.key) }
        // Raising a key again only updates it: an alarm that stays does not ring again.
        held.forEach { (alarm, seenAt) -> pumpAlerts.raise(alertFor(alarm, raisedAt = seenAt, lifetime = PumpAlertLifetime.CONDITION)) }
    }

    private fun alertFor(alarm: AlarmState, raisedAt: Long, lifetime: PumpAlertLifetime) = PumpAlert(
        source = SOURCE,
        key = alarm.name,
        severity = severityOf(alarm),
        title = aaruyPump.alarmText(alarm),
        raisedAt = raisedAt,
        // The pump-side step, through the queue (A5). The pump has no step for a stop or an empty reservoir.
        action = if (alarm.isConfirmedOnThePump()) PumpAlertAction(AaruyStrings.confirm_alarms_label) {
            commandQueue.customCommand(AaruyConfirmAlarmsCommand()).success
        } else null,
        lifetime = lifetime
    )

    companion object {

        const val SOURCE = "aaruy"

        /**
         * As the pump treats them (A3). Delivery stopped or not safe: the empty reservoir, the motor and battery faults, a
         * pump that is off or stopped too long. Soon: low reservoir, low battery, a stop in 15 minutes. A refused command
         * is reported by the command that failed, so its alert is only for information.
         */
        fun severityOf(alarm: AlarmState): PumpAlertSeverity = when {
            AlarmState.isHighAlarm(alarm) || alarm == AlarmState.PUMP_STOP_TOO_LONG -> PumpAlertSeverity.CRITICAL
            AlarmState.isLowAlarm(alarm) || alarm == AlarmState.PUMP_STOP_15M_LATER -> PumpAlertSeverity.WARNING
            else                                                                    -> PumpAlertSeverity.INFO
        }
    }
}
