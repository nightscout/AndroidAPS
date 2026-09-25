package app.aaps.plugins.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.automation.R as AutomationR
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ReminderSchedulerImpl(
    private val context: Context,
    private val rh: TextResolver,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ReminderScheduler {

    /**
     * Schedule a reminder that rings [seconds] from now.
     *
     * Uses AlarmManager + [TimerReminderReceiver] → [app.aaps.core.interfaces.ui.UiInteraction.runAlarm] (a
     * background-safe alarm) instead of the system Clock app's `ACTION_SET_TIMER`: that needs
     * `startActivity`, which Android blocks from the background, so a reminder scheduled while AAPS is
     * backgrounded (e.g. a client-relayed Bolus-Wizard "Set alarm" delivered on the master) was silently lost.
     * `setAlarmClock` is exact and fires while the device is idle, but it needs `SCHEDULE_EXACT_ALARM` unless
     * AAPS is excluded from battery optimization. Without either, the call is not made and the user is told
     * the reminder could not be set; `PluginPermissionsImpl` asks for the permission.
     *
     * @param seconds seconds in the future to ring
     * @param text alarm message
     */
    override fun scheduleReminder(seconds: Int, text: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                aapsLogger.warn(LTag.AUTOMATION, "Exact alarms not allowed - reminder \"$text\" not set")
                rxBus.send(EventShowSnackbar(rh.gs(AutomationStrings.error_setting_reminder), EventShowSnackbar.Type.Error))
                return
            }
            val triggerAt = dateUtil.now() + seconds * 1000L
            val intent = Intent(context, TimerReminderReceiver::class.java)
                .putExtra(TimerReminderReceiver.EXTRA_TEXT, text)
            val pendingIntent = PendingIntent.getBroadcast(
                context, text.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pendingIntent), pendingIntent)
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Reminder \"$text\" not set", e)
            rxBus.send(EventShowSnackbar(rh.gs(AutomationStrings.error_setting_reminder), EventShowSnackbar.Type.Error))
        }
    }
}
