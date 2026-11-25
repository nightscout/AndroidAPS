package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertAction
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.ext.transformNotificationStringResources
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.coroutines.cancellation.CancellationException

/**
 * Presentation half of the alarm pipeline: observes the persisted active-alarm set (via [CarelevoAlarmActionHandler]),
 * keeps it in [alarms] for the Carelevo screen, and shows it through [Alerts] ([showAlerts]). The core does the
 * rest - the card, the sound, the alarm screen, the system notification in the background, silencing - the same as for
 * every driver. See `_docs/PUMP_DESIGN.md`, section 2.
 */
@SingleIn(AppScope::class)
class CarelevoAlarmNotifier @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val alerts: Alerts,
    private val preferences: Preferences,
    private val alarmActionHandler: CarelevoAlarmActionHandler,
    private val rh: TextResolver
) {

    /** Where alarms are handled: the main thread, so the handling never runs twice at once. A test sets an unconfined dispatcher. */
    internal var dispatcher: CoroutineDispatcher = Dispatchers.Main
    private val scope: CoroutineScope by lazy { CoroutineScope(dispatcher + SupervisorJob()) }
    private val _alarms = MutableStateFlow<List<CarelevoAlarmInfo>>(emptyList())
    val alarms = _alarms.asStateFlow()
    private var onAlarmsUpdated: ((List<CarelevoAlarmInfo>) -> Unit)? = null

    fun startObserving(
        onAlarmsUpdated: (List<CarelevoAlarmInfo>) -> Unit
    ) {
        this.onAlarmsUpdated = onAlarmsUpdated

        // Asked for here, as before: the store loads its alarms on the caller's thread, not on the main thread.
        val stored = alarmActionHandler.observeAlarms()
        scope.launch {
            stored
                .catch { e -> aapsLogger.error(LTag.PUMPCOMM, "observeAlarms.error error=$e") }
                .collect { alarms -> handleAlarmsInternal(alarms) }
        }
    }

    fun refreshAlarms() {
        scope.launch {
            try {
                handleAlarmsInternal(alarmActionHandler.getAlarmsOnce())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPCOMM, "refreshAlarms.error error=$e")
            }
        }
    }

    private fun handleAlarmsInternal(alarms: List<CarelevoAlarmInfo>) {
        aapsLogger.debug(LTag.PUMPCOMM, "handleAlarmsInternal alarms=$alarms")
        _alarms.value = alarms
        onAlarmsUpdated?.invoke(alarms)
    }

    fun stopObserving() {
        scope.coroutineContext.cancelChildren()
    }

    /**
     * Every stored alarm is one pump alert, keyed by its alarm id; one no longer stored has ended. An alarm raised again
     * keeps its id and its creation time, so the core does not ring it again, also not after a restart. The patch that
     * was not stopped is not a stored alarm, so it is left as it is.
     */
    fun showAlerts(alarms: List<CarelevoAlarmInfo>) {
        val keys = alarms.map { it.alarmId }.toSet()
        alerts.active.value
            .filter { it.source == SOURCE && it.kind != CarelevoAlertKind.NOT_STOPPED && it.key !in keys }
            .forEach { alerts.resolve(SOURCE, it.key) }
        alarms.forEach { alerts.raise(toAlert(it)) }
    }

    /** The patch is gone or the driver stops: none of its alerts is left. */
    fun clearAlerts() = alerts.resolveAll(SOURCE)

    /**
     * The patch was removed from AAPS without being reached, so it was not told to stop and may still give insulin
     * that AAPS does not count. It rings until the user answers, and the answer ends it: nothing on the patch side
     * will ever end it.
     */
    fun patchNotStopped(now: Long) = alerts.raise(
        Alert(
            kind = CarelevoAlertKind.NOT_STOPPED,
            key = NOT_STOPPED_KEY,
            severity = AlertSeverity.CRITICAL,
            title = CarelevoAlertKind.NOT_STOPPED.title,
            text = CarelevoStrings.carelevo_discarded_not_reached,
            raisedAt = now,
            lifetime = AlertLifetime.EVENT
        )
    )

    internal fun toAlert(alarm: CarelevoAlarmInfo): Alert {
        val (titleRes, descRes, btnRes) = alarm.cause.transformNotificationStringResources()
        val descArgs = buildDescArgsFor(alarm)
        return Alert(
            kind = CarelevoAlertKind.of(alarm.cause),
            key = alarm.alarmId,
            // A pump has no glucose alarm, so every cause has a severity.
            severity = alarm.cause.effect.severity ?: AlertSeverity.CRITICAL,
            title = titleRes,
            text = descRes?.let { if (descArgs.isEmpty()) it else it.withArgs(*descArgs.toTypedArray()) },
            raisedAt = raisedAt(alarm),
            // The clear runs the per-cause flow of the action handler, with its own fallbacks; the alert ends when that
            // flow removes the alarm from the store.
            action = AlertAction(btnRes) {
                alarmActionHandler.triggerEvent(AlarmEvent.ClearAlarm(info = alarm))
                true
            }
        )
    }

    /** The stored creation time: local ISO text with no offset, read in the zone of the phone. */
    private fun raisedAt(alarm: CarelevoAlarmInfo): Long = try {
        LocalDateTime.parse(alarm.createdAt).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (e: IllegalArgumentException) {
        aapsLogger.error(LTag.PUMPCOMM, "alarm ${alarm.alarmId} has no readable creation time: ${alarm.createdAt}", e)
        0L
    }

    private fun buildDescArgsFor(alarm: CarelevoAlarmInfo): List<String> = when (alarm.cause) {
        AlarmCause.ALARM_NOTICE_LOW_INSULIN,
        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN -> {
            val lowInsulinNoticeAmount = preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)
            listOf((lowInsulinNoticeAmount).toString())
        }

        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED -> {
            val expiry = preferences.get(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)
            aapsLogger.debug(LTag.PUMPCOMM, "buildDescArgsFor alarm=${alarm.value} expiry=$expiry")
            val (days, hours) = splitDaysAndHours(expiry)
            listOf(days.toString(), hours.toString())
        }

        AlarmCause.ALARM_NOTICE_BG_CHECK      -> {
            val totalMinutes = alarm.value ?: 0
            listOf(formatBgCheckDuration(totalMinutes))
        }

        else                                  -> emptyList()
    }

    private fun splitDaysAndHours(totalHours: Int): Pair<Int, Int> {
        val days = totalHours / 24
        val hours = totalHours % 24
        return days to hours
    }

    private fun formatBgCheckDuration(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 ->
                rh.gs(CarelevoStrings.common_label_unit_value_duration_hour_and_minute, hours, minutes)

            hours > 0                ->
                rh.gs(CarelevoStrings.common_label_unit_value_duration_hour, hours)

            else                     ->
                rh.gs(CarelevoStrings.common_label_unit_value_minute, minutes)
        }
    }

    companion object {

        /** The source of every Carelevo pump alert. */
        const val SOURCE = "carelevo"

        /** The key of the alert for a patch that was removed without being stopped. A stored alarm's id is a UUID or a time in ms. */
        const val NOT_STOPPED_KEY = "not_stopped"
    }
}
