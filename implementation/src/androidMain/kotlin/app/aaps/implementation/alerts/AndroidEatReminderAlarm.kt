package app.aaps.implementation.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
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
 * - **The alarm** is a booked alert ([UiInteraction.scheduleEatReminderAlarm], `_docs/ALARMS.md` step 1e): the core
 *   books it with an exact alarm and raises it at the end, with its +5 min.
 * - **The countdown** is a silent notification while the timer runs: Android draws the time left itself
 *   (a count-down chronometer), so nothing here wakes up every second. It has Stop and +5 min, so the timer can
 *   be handled from the lock screen without opening AAPS. It has its own channel at low importance (no sound,
 *   no pop-up), which the user can turn off on its own. It goes away by itself at the end, when the alarm
 *   takes over.
 *
 * A new call replaces the old alarm (the same alert key) and notification (a fixed id).
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
    // A provider: UiInteraction reaches the timer back for its +5 min.
    private val uiInteraction: () -> UiInteraction,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : EatReminderAlarm {

    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun schedule(startedAt: Long, endsAt: Long): Boolean {
        // Not booked without exact alarms (PluginPermissionsImpl asks for them): then the timer is not started at all.
        if (!uiInteraction().scheduleEatReminderAlarm(endsAt, status = rh.gs(CoreUiStrings.eat_reminder_title), title = rh.gs(config.appName))) {
            aapsLogger.error(LTag.AUTOMATION, "Eat reminder not set: its alarm could not be booked")
            rxBus.send(EventShowSnackbar(rh.gs(CoreUiStrings.eat_reminder_not_set), EventShowSnackbar.Type.Error))
            return false
        }
        postCountdown(startedAt, endsAt)
        return true
    }

    override fun cancel() {
        uiInteraction().cancelScheduledAlarm(UiInteraction.KEY_EAT_REMINDER)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun postCountdown(startedAt: Long, endsAt: Long) {
        createChannel()
        val left = endsAt - dateUtil.now()
        if (left <= 0) return
        val totalMinutes = T.msecs(endsAt - startedAt).mins().toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconsProvider.getNotificationIcon())
            .setContentTitle(rh.gs(CoreUiStrings.eat_reminder_title))
            // A minimized notification collapses to "title text" on one line, where the system does
            // not draw its countdown. So the body says when the reminder rings, as a time of day.
            // Each +5 min posts the notification again with the new time.
            .setContentText(rh.gs(CoreUiStrings.eat_reminder_rings_at, dateUtil.timeString(endsAt)))
            // The header carries the whole wait, so a short body line cannot push it out.
            .setSubText(rh.gs(CoreUiStrings.eat_reminder_total_wait, rh.gs(CoreUiStrings.format_mins, totalMinutes)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // The watch app posts its own countdown from the status it gets, with its own Stop and
            // +5 min, so a copy of this one on the watch would say the same thing twice. The alarm
            // at the end is a different notification and still reaches the watch.
            .setLocalOnly(true)
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

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, EatReminderReceiver.requestCodeOf(action),
            Intent(context, EatReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {

        const val CHANNEL_ID = "aaps_eat_reminder"
        const val NOTIFICATION_ID = 4720
    }
}
