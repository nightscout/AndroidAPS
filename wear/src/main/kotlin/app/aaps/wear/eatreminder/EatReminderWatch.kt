package app.aaps.wear.eatreminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderAction
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Date
import kotlin.math.max

/**
 * The watch's copy of the phone's "time to eat" timer.
 *
 * The phone owns the timer and sends its end time with every status. The watch draws the end in its BG
 * graph and sets its own alarm at that time, so it rings also when the phone is out of reach at that
 * moment. While it runs a silent notification with Stop and +5 min sits in the stream, so the timer can
 * be handled from the watch before it rings; it is the only way to do that on the watch, so it comes
 * back when it is swiped away. The ring is a vibrating notification with Silence and +5 min, and a
 * swipe silences it, as on a clock's alarm; a screen of its own was tried, but Android does not open one
 * from an alarm in the background. A watch vibrates once per notification, so the ring is posted again
 * every half minute for five minutes, as the phone rings until it is answered.
 * The buttons go to the phone, which stops or moves the timer, and the next status brings the result
 * back. Until then the watch acts on its own copy, so it reads right also with the phone away.
 */
@SingleIn(AppScope::class)
@Inject
class EatReminderWatch(
    private val context: Context,
    private val sp: SP,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger
) {

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    /** When the timer the watch knows rings, 0 when none runs. */
    val endsAt: Long get() = sp.getLong(R.string.key_eat_reminder_ends_at, 0L)

    /**
     * The phone's timer, from a status. Null ends it. Returns true when the end time changed, so the
     * caller can redraw the graph tile.
     */
    fun onStatus(endsAt: Long?): Boolean {
        val new = endsAt ?: 0L
        if (new == this.endsAt) return false
        sp.putLong(R.string.key_eat_reminder_ends_at, new)
        // Whatever rang is over: the timer ended, or it was moved later
        clearRing()
        if (new == 0L) {
            aapsLogger.debug(LTag.WEAR, "Eat reminder ended on the phone")
            alarmManager.cancel(ringIntent())
            notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
        } else {
            aapsLogger.debug(LTag.WEAR, "Eat reminder ends at $new")
            schedule(new)
        }
        return true
    }

    /**
     * The alarm fired, or one of its repeats. Rings only for the timer the watch knows now, not for one that
     * was moved or ended. Then it sets the next repeat, every [RING_REPEAT_MS] until [RING_REPEAT_FOR_MS] after
     * the end. Every answer, on the watch or on the phone, cancels or replaces the alarm, and the repeats with it.
     */
    fun ring() {
        val endsAt = this.endsAt
        val now = System.currentTimeMillis()
        if (endsAt == 0L || now < endsAt - RING_TOLERANCE_MS || now > endsAt + RING_REPEAT_FOR_MS + RING_TOLERANCE_MS) {
            aapsLogger.debug(LTag.WEAR, "Eat reminder alarm ignored: no timer due now")
            return
        }
        aapsLogger.debug(LTag.WEAR, "Eat reminder rings")
        notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
        postRingNotification()
        val next = now + RING_REPEAT_MS
        if (next <= endsAt + RING_REPEAT_FOR_MS) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, ringIntent())
    }

    /** Silence: the timer ends. The phone silences its own alarm too. */
    fun silence() {
        send(EatReminderAction.STOP)
        sp.remove(R.string.key_eat_reminder_ends_at)
        alarmManager.cancel(ringIntent())
        notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
        clearRing()
    }

    /**
     * +5 min: the end moves later, as on the phone: 5 minutes on top of the end while the timer runs,
     * 5 minutes from now once it has rung. The phone's own answer comes with the next status.
     */
    fun extend() {
        send(EatReminderAction.EXTEND)
        val newEnd = max(endsAt, System.currentTimeMillis()) + EXTEND_MS
        sp.putLong(R.string.key_eat_reminder_ends_at, newEnd)
        clearRing()
        schedule(newEnd)
    }

    /** The running timer's notification was swiped away. It comes back while the timer runs: it is the watch's only Stop. */
    fun repost() {
        val endsAt = this.endsAt
        if (endsAt == 0L) return
        aapsLogger.debug(LTag.WEAR, "Eat reminder notification dismissed, posted again")
        postCountdownNotification(endsAt)
    }

    private fun send(action: EatReminderAction) {
        rxBus.send(EventWearToMobile(EventData.ActionEatReminder(action)))
    }

    private fun schedule(endsAt: Long) {
        // Long past: the phone rang while the watch was away, no use ringing here now
        if (endsAt < System.currentTimeMillis() - RING_TOLERANCE_MS) return
        // Exact, so a kitchen timer is not minutes late. The watch app targets API 30, so no permission is asked for
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, ringIntent())
        postCountdownNotification(endsAt)
    }

    /**
     * The running timer in the stream: silent, with Stop and +5 min, and the ring time as its time. It
     * goes away by itself at the end, when the ring takes over. A live countdown is not reliable on a
     * watch, so the text names the time instead. Wear OS lets the user swipe an ongoing notification
     * away, so the swipe posts it again; our own cancel does not fire the delete intent.
     */
    private fun postCountdownNotification(endsAt: Long) {
        val left = endsAt - System.currentTimeMillis()
        if (left <= 0) return
        notificationManager.createNotificationChannel(
            NotificationChannel(COUNTDOWN_CHANNEL_ID, context.getString(R.string.eat_reminder_countdown_channel), NotificationManager.IMPORTANCE_LOW).apply {
                enableVibration(false)
                setSound(null, null)
            }
        )
        val notification = NotificationCompat.Builder(context, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.eat_reminder_title))
            .setContentText(context.getString(R.string.eat_reminder_rings_at, DateFormat.getTimeFormat(context).format(Date(endsAt))))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(endsAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(left)
            .setDeleteIntent(actionIntent(EatReminderReceiver.ACTION_REPOST, REPOST_REQUEST_CODE))
            .addAction(0, context.getString(R.string.eat_reminder_stop), actionIntent(EatReminderReceiver.ACTION_STOP, STOP_REQUEST_CODE))
            .addAction(0, context.getString(R.string.eat_reminder_extend, EatReminder.EXTEND_MINUTES), actionIntent(EatReminderReceiver.ACTION_EXTEND, EXTEND_REQUEST_CODE))
            .build()
        notificationManager.notify(COUNTDOWN_NOTIFICATION_ID, notification)
    }

    private fun clearRing() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * The ring: Silence and +5 min as buttons, and a swipe silences too. Our own cancel does not fire the delete
     * intent. Posted again on every repeat, so the watch vibrates again: not alert-once on purpose.
     */
    private fun postRingNotification() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.eat_reminder_title), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = RING_VIBRATION
            }
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.eat_reminder_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDeleteIntent(actionIntent(EatReminderReceiver.ACTION_STOP, STOP_REQUEST_CODE))
            .addAction(0, context.getString(R.string.eat_reminder_silence), actionIntent(EatReminderReceiver.ACTION_STOP, STOP_REQUEST_CODE))
            .addAction(0, context.getString(R.string.eat_reminder_extend, EatReminder.EXTEND_MINUTES), actionIntent(EatReminderReceiver.ACTION_EXTEND, EXTEND_REQUEST_CODE))
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ringIntent(): PendingIntent = actionIntent(EatReminderReceiver.ACTION_RING, RING_REQUEST_CODE)

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, EatReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {

        const val CHANNEL_ID = "AndroidAPS-EatReminder"
        const val COUNTDOWN_CHANNEL_ID = "AndroidAPS-EatReminder-Countdown"
        const val NOTIFICATION_ID = 4720
        const val COUNTDOWN_NOTIFICATION_ID = 4724
        const val RING_REQUEST_CODE = 4720
        const val STOP_REQUEST_CODE = 4721
        const val EXTEND_REQUEST_CODE = 4722
        const val REPOST_REQUEST_CODE = 4723
        const val EXTEND_MS = EatReminder.EXTEND_MINUTES * 60_000L

        /** An alarm may come a little early or late; further from the end it belongs to an older timer. */
        const val RING_TOLERANCE_MS = 2 * 60_000L

        /** The ring is repeated this often, for this long after the end, until it is answered. */
        const val RING_REPEAT_MS = 30_000L
        const val RING_REPEAT_FOR_MS = 5 * 60_000L
        val RING_VIBRATION = longArrayOf(0, 500, 300, 500, 300, 500)
    }
}
