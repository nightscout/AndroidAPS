package app.aaps.implementation.alerts

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The Android side of the "time to eat" timer.
 *
 * - **The alarm** is `AlarmManager.setAlarmClock`, which is exact and fires while the phone is idle, as for the
 *   other AAPS reminders. It sends [EatReminderReceiver], which rings the normal AAPS alarm.
 * - **The countdown** is a silent notification while the timer runs: Android draws the time left itself
 *   (a count-down chronometer), so nothing here wakes up every second. It has Stop and +5 min, so the timer can
 *   be handled from the lock screen without opening AAPS. It has its own channel at low importance (no sound,
 *   no pop-up), which the user can turn off on its own. It goes away by itself at the end, when the alarm
 *   takes over.
 *
 * Both have fixed request codes, so a new call replaces the old alarm and notification.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidEatReminderAlarm(
    private val context: Context,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val iconsProvider: IconsProvider,
    private val notificationHolder: () -> NotificationHolder,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : EatReminderAlarm {

    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun schedule(startedAt: Long, endsAt: Long): Boolean {
        // setAlarmClock needs SCHEDULE_EXACT_ALARM unless AAPS is excluded from battery optimization.
        // PluginPermissionsImpl asks for it; without it the timer is not started at all.
        if (!alarmManager.canScheduleExactAlarms()) return notSet(null)
        try {
            val show = notificationHolder().openAppIntent() ?: ringIntent()
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(endsAt, show), ringIntent())
        } catch (e: SecurityException) {
            // The permission was taken away between the check above and this call.
            return notSet(e)
        }
        postCountdown(startedAt, endsAt)
        return true
    }

    override fun cancel() {
        alarmManager.cancel(ringIntent())
        cancelCountdown()
    }

    /** Removes the countdown. The receiver does it when the alarm rings; the notification would also time out. */
    fun cancelCountdown() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun notSet(e: Exception?): Boolean {
        if (e != null) aapsLogger.error(LTag.AUTOMATION, "Exact alarms revoked - eat reminder not set", e)
        else aapsLogger.error(LTag.AUTOMATION, "Exact alarms not allowed - eat reminder not set")
        rxBus.send(EventShowSnackbar(rh.gs(CoreUiStrings.eat_reminder_not_set), EventShowSnackbar.Type.Error))
        return false
    }

    private fun postCountdown(startedAt: Long, endsAt: Long) {
        createChannel()
        val left = endsAt - dateUtil.now()
        if (left <= 0) return
        val totalMinutes = T.msecs(endsAt - startedAt).mins().toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setContentTitle(rh.gs(CoreUiStrings.eat_reminder_title))
            .setContentText(rh.gs(CoreUiStrings.eat_reminder_total_wait, rh.gs(CoreUiStrings.format_mins, totalMinutes)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(left)
            .setContentIntent(notificationHolder().openAppIntent())
            .addAction(0, rh.gs(CoreUiStrings.stop), actionIntent(EatReminderReceiver.ACTION_STOP))
            .addAction(0, rh.gs(CoreUiStrings.eat_reminder_extend, EatReminder.EXTEND_MINUTES), actionIntent(EatReminderReceiver.ACTION_EXTEND))
            .build()
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked. The alarm is set and will ring; only the countdown is missing.
            aapsLogger.error(LTag.NOTIFICATION, "Eat reminder countdown not shown - POST_NOTIFICATIONS likely revoked", e)
        }
    }

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.eat_reminder_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.eat_reminder_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun ringIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context, RING_REQUEST_CODE,
            Intent(context, EatReminderReceiver::class.java).setAction(EatReminderReceiver.ACTION_RING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, EatReminderReceiver.requestCodeOf(action),
            Intent(context, EatReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {

        const val CHANNEL_ID = "aaps_eat_reminder"
        const val NOTIFICATION_ID = 4720
        const val RING_REQUEST_CODE = 4720
    }
}
