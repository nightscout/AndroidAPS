package app.aaps.wear.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.wear.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The phone's alarm on the watch: a pump error, an automation alarm, a BG alarm. The phone sends the title and
 * text of its alarm screen when the alarm rings, and a vibrating notification shows them here, so the watch tells
 * why the phone makes a sound and the alarm can be answered from the wrist, with the same answers as the "time to
 * eat" alarm: Silence stops the alarm on the phone and closes its screen, and a swipe does the same. Mute 5 min
 * silences it, and the phone rings again after five minutes, with a new notification here. Mute 5 min is shown
 * only when the phone can honour it: an urgent notification with a sound, as "Pump unreachable", has only Dismiss
 * on the phone, so here it has only Silence. The phone rings until it is answered, and a watch vibrates once per
 * notification, so the notification is posted again every half minute for five minutes. An answer on any side
 * takes it away: the phone reports every stop. The "time to eat" alarm is not sent here, the watch rings that one
 * by itself ([app.aaps.wear.eatreminder.EatReminderWatch]).
 *
 * The title and text are kept in preferences, so a repeat can post them again also when the process was gone in
 * between.
 */
@SingleIn(AppScope::class)
@Inject
class PhoneAlarmWatch(
    private val context: Context,
    private val sp: SP,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger
) {

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    /** An alarm rings on the phone. A newer one takes the place of the one shown, as on the phone. */
    fun show(alarm: EventData.AlarmRinging) {
        aapsLogger.debug(LTag.WEAR, "Phone alarm: ${alarm.title} - ${alarm.status}")
        sp.putString(R.string.key_phone_alarm_title, alarm.title)
        sp.putString(R.string.key_phone_alarm_text, alarm.status)
        sp.putLong(R.string.key_phone_alarm_since, System.currentTimeMillis())
        sp.putBoolean(R.string.key_phone_alarm_can_mute, alarm.canMute)
        post(alarm.title, alarm.status, alarm.canMute)
        scheduleRepeat()
    }

    /** A repeat: posted again so the watch vibrates again, as long as the phone has not reported a stop. */
    fun repeat() {
        if (sp.getLong(R.string.key_phone_alarm_since, 0L) == 0L) return
        post(
            sp.getString(R.string.key_phone_alarm_title, ""),
            sp.getString(R.string.key_phone_alarm_text, ""),
            sp.getBoolean(R.string.key_phone_alarm_can_mute, false)
        )
        scheduleRepeat()
    }

    /** Silence, or a swipe: the alarm on the phone stops and its screen closes. */
    fun silence() {
        rxBus.send(EventWearToMobile(EventData.DismissAlert(System.currentTimeMillis())))
        clear()
    }

    /** Mute 5 min: the alarm on the phone is silenced and rings again after the minutes, here too. */
    fun mute() {
        rxBus.send(EventWearToMobile(EventData.MuteAlert(System.currentTimeMillis(), UiInteraction.MUTE_MINUTES)))
        clear()
    }

    /** The alarm is over, answered on the phone or here. */
    fun clear() {
        alarmManager.cancel(repeatIntent())
        notificationManager.cancel(NOTIFICATION_ID)
        sp.remove(R.string.key_phone_alarm_title)
        sp.remove(R.string.key_phone_alarm_text)
        sp.remove(R.string.key_phone_alarm_since)
        sp.remove(R.string.key_phone_alarm_can_mute)
    }

    private fun scheduleRepeat() {
        val next = System.currentTimeMillis() + REPEAT_MS
        if (next <= sp.getLong(R.string.key_phone_alarm_since, 0L) + REPEAT_FOR_MS)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, repeatIntent())
    }

    /**
     * Silence, and Mute 5 min when the phone can honour it, as buttons, and a swipe silences. Posted again on every
     * repeat, so the watch vibrates again: not alert-once on purpose. Our own cancel does not fire the delete intent.
     */
    private fun post(title: String, text: String, canMute: Boolean) {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.phone_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = VIBRATION
            }
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDeleteIntent(actionIntent(PhoneAlarmReceiver.ACTION_SILENCE, SILENCE_REQUEST_CODE))
            .addAction(0, context.getString(R.string.phone_alarm_silence), actionIntent(PhoneAlarmReceiver.ACTION_SILENCE, SILENCE_REQUEST_CODE))
        if (canMute)
            builder.addAction(0, context.getString(R.string.phone_alarm_mute, UiInteraction.MUTE_MINUTES), actionIntent(PhoneAlarmReceiver.ACTION_MUTE, MUTE_REQUEST_CODE))
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun repeatIntent(): PendingIntent = actionIntent(PhoneAlarmReceiver.ACTION_REPEAT, REPEAT_REQUEST_CODE)

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, PhoneAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {

        const val CHANNEL_ID = "AndroidAPS-PhoneAlarm"
        const val NOTIFICATION_ID = 4730
        const val REPEAT_REQUEST_CODE = 4730
        const val SILENCE_REQUEST_CODE = 4731
        const val MUTE_REQUEST_CODE = 4732

        /** The notification is posted again this often, for this long after the alarm rang, until it is answered. */
        const val REPEAT_MS = 30_000L
        const val REPEAT_FOR_MS = 5 * 60_000L
        val VIBRATION = longArrayOf(0, 500, 300, 500, 300, 500)
    }
}
