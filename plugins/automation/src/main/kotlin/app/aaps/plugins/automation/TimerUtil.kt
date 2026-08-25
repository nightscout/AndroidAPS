package app.aaps.plugins.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class TimerUtil @Inject constructor(
    private val context: Context,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil
) {

    /**
     * Schedule a reminder that rings [seconds] from now.
     *
     * Uses AlarmManager + [TimerReminderReceiver] → [app.aaps.core.interfaces.ui.UiInteraction.runAlarm] (a
     * background-safe full-screen-intent alarm) instead of the system Clock app's `ACTION_SET_TIMER`: that
     * needs `startActivity`, which Android blocks from the background, so a reminder scheduled while AAPS is
     * backgrounded (e.g. a client-relayed Bolus-Wizard "Set alarm" delivered on the master) was silently lost.
     * `setAlarmClock` is exact, fires while the device is idle, and needs no `SCHEDULE_EXACT_ALARM` permission.
     *
     * @param seconds seconds in the future to ring
     * @param text alarm message
     */
    fun scheduleReminder(seconds: Int, text: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = dateUtil.now() + seconds * 1000L
            val intent = Intent(context, TimerReminderReceiver::class.java)
                .putExtra(TimerReminderReceiver.EXTRA_TEXT, text)
            val pendingIntent = PendingIntent.getBroadcast(
                context, text.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pendingIntent), pendingIntent)
        } catch (_: Exception) {
            rxBus.send(EventShowSnackbar(rh.gs(R.string.error_setting_reminder), EventShowSnackbar.Type.Error))
        }
    }
}
