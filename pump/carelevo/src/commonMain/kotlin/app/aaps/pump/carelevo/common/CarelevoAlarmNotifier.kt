package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType.Companion.isCritical
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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Presentation half of the alarm pipeline: observes the persisted active-alarm set (via
 * [CarelevoAlarmActionHandler]) and surfaces it — the in-app AAPS "top notification" cards
 * ([showTopNotification], with the clear action wired back to the shared state machine), the
 * phone notification for backgrounded users ([showNotification], through [CarelevoSystemNotifier]), and the
 * [alarms] StateFlow.
 * `CarelevoPumpPlugin.handleAlarms` additionally routes a critical alarm through the shared AAPS
 * full-screen alarm (`UiInteraction.runAlarm`) — that escalation is independent of this class.
 *
 * [showTopNotification] deliberately never posts at [app.aaps.core.interfaces.notifications.NotificationLevel.URGENT]
 * (see the comment there) — that level is `NotificationManagerImpl`'s own alarm tier, which the
 * shared full-screen alarm's OK button silences *app-wide* on confirm (`muteAllAlarms`), wiping any
 * URGENT+sound card off the screen without actually acknowledging it. Matching `eopatch`'s
 * `AlarmManager.showNotification` pattern, the sound/full-screen escalation is left entirely to
 * `runAlarm`, so the card survives that confirm and stays until the user genuinely clears the
 * alarm through it.
 */
@SingleIn(AppScope::class)
class CarelevoAlarmNotifier @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val notificationManager: NotificationManager,
    private val preferences: Preferences,
    private val alarmActionHandler: CarelevoAlarmActionHandler,
    private val rh: TextResolver,
    private val systemNotifier: CarelevoSystemNotifier,
    private val appForeground: CarelevoAppForeground
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

        if (!appForeground.isInForeground()) {
            alarms.forEach { alarm ->
                showNotification(alarm)
            }
        }

        onAlarmsUpdated?.invoke(alarms)
    }

    fun showTopNotification(alarms: List<CarelevoAlarmInfo>) {
        // CARELEVO_PATCH_ALERT is allowMultiple=true (a fresh instance per post with no replace), so
        // clear the previous carelevo cards before re-posting the current set. This gives
        // replace-on-refresh semantics while still letting distinct concurrent alarms coexist.
        notificationManager.dismiss(NotificationId.CARELEVO_PATCH_ALERT)
        alarms.forEach { newAlarm ->
            val (titleRes, descRes, btnRes) = newAlarm.cause.transformNotificationStringResources()

            val descArgs = buildDescArgsFor(newAlarm)
            val desc = buildDescription(descRes, descArgs)
            aapsLogger.debug(LTag.PUMPCOMM, "showTopNotification titleRes=$titleRes descArgs=$descArgs desc=$desc")
            // Critical tiers get IMPORTANT (not URGENT) — deliberately below NotificationManagerImpl's
            // alarm tier, matching eopatch's AlarmManager.showNotification. URGENT+sound is what the
            // shared full-screen alarm's OK button silences app-wide on confirm (muteAllAlarms), which
            // would wipe this card without it ever being acknowledged; sound/full-screen is runAlarm's
            // job alone (CarelevoPumpPlugin.handleAlarms). Neither severity auto-expires — an unhandled
            // alarm, critical or not, must not disappear on its own.
            val critical = newAlarm.alarmType.isCritical()
            notificationManager.post(
                id = NotificationId.CARELEVO_PATCH_ALERT,
                text = rh.gs(titleRes) + "\n" + desc,
                level = if (critical) NotificationLevel.IMPORTANT else NotificationLevel.NORMAL,
                actions = listOf(
                    NotificationAction(btnRes) {
                        alarmActionHandler.triggerEvent(AlarmEvent.ClearAlarm(info = newAlarm))
                    }
                ),
                date = dateUtil.now(),
                // No sound here on purpose (see the level comment above) - runAlarm owns the escalation.
                sound = null,
                validityCheck = null,
            )
        }
    }

    fun stopObserving() {
        scope.coroutineContext.cancelChildren()
    }

    fun showNotification(alarm: CarelevoAlarmInfo) {
        val (titleRes, descRes, _) = alarm.cause.transformNotificationStringResources()
        systemNotifier.show(alarm.alarmId, rh.gs(titleRes), buildNotificationDescription(alarm, descRes))
    }

    /** Builds the final body text from the alarm metadata and descRes. */
    private fun buildNotificationDescription(
        alarm: CarelevoAlarmInfo,
        descRes: TextRef?
    ): String {
        if (descRes == null) return ""
        return when (alarm.cause) {
            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            AlarmCause.ALARM_NOTICE_LOW_INSULIN -> {
                val remain = (alarm.value ?: 0).toString()
                rh.gs(descRes, remain)
            }

            AlarmCause.ALARM_NOTICE_PATCH_EXPIRED -> {
                formatPatchExpired(descRes, alarm.value ?: 0)
            }

            AlarmCause.ALARM_NOTICE_BG_CHECK -> {
                val span = formatBgCheckSpan(alarm.value ?: 0)
                rh.gs(descRes, span)
            }

            else -> rh.gs(descRes)
        }
    }

    private fun formatPatchExpired(descRes: TextRef, totalHours: Int): String {
        val days = totalHours / 24
        val remainHours = totalHours % 24
        return rh.gs(descRes, days, remainHours)
    }

    private fun formatBgCheckSpan(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            rh.gs(
                CarelevoStrings.common_label_unit_value_duration_hour_and_minute,
                hours, minutes
            )
        } else {
            rh.gs(
                CarelevoStrings.common_label_unit_value_minute,
                minutes
            )
        }
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

    private fun buildDescription(descRes: TextRef?, args: List<String>): String {
        return descRes?.let { resId ->
            if (args.isEmpty()) rh.gs(resId) else rh.gs(resId, *args.toTypedArray())
        } ?: ""
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
}
