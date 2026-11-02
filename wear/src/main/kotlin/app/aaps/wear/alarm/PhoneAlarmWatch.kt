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
import app.aaps.core.interfaces.notifications.DoNotDisturbRule
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
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
 *
 * While the watch is in Do Not Disturb and the phone's "Override Do Not Disturb mode" is off, the notification
 * only vibrates, as the phone's alarm does then, see [DoNotDisturbRule].
 *
 * An alarm that has buttons on the phone, as a Nightscout alarm with Snooze 15, 30 and 60 min, shows those buttons
 * here instead, in the same order, and a swipe presses the first. Silence would only stop the sound: it does not tell
 * Nightscout the alarm was answered, so Nightscout sent the same alarm again a minute later.
 */
@SingleIn(AppScope::class)
@Inject
class PhoneAlarmWatch(
    private val context: Context,
    private val sp: SP,
    private val preferences: Preferences,
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
        sp.putInt(R.string.key_phone_alarm_key, alarm.alarmKey)
        sp.putString(R.string.key_phone_alarm_actions, alarm.actions.joinToString(ACTIONS_SEPARATOR))
        post(alarm.title, alarm.status, alarm.canMute, alarm.actions)
        scheduleRepeat()
    }

    /** A repeat: posted again so the watch vibrates again, as long as the phone has not reported a stop. */
    fun repeat() {
        if (sp.getLong(R.string.key_phone_alarm_since, 0L) == 0L) return
        post(
            sp.getString(R.string.key_phone_alarm_title, ""),
            sp.getString(R.string.key_phone_alarm_text, ""),
            sp.getBoolean(R.string.key_phone_alarm_can_mute, false),
            storedActions()
        )
        scheduleRepeat()
    }

    /** A button of the phone's notification, or the swipe (the first button): the phone runs the same button. */
    fun runAction(index: Int) {
        rxBus.send(EventWearToMobile(EventData.AlarmAction(System.currentTimeMillis(), sp.getInt(R.string.key_phone_alarm_key, 0), index)))
        clear()
    }

    private fun storedActions(): List<String> =
        sp.getString(R.string.key_phone_alarm_actions, "").split(ACTIONS_SEPARATOR).filter { it.isNotEmpty() }

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
        sp.remove(R.string.key_phone_alarm_key)
        sp.remove(R.string.key_phone_alarm_actions)
    }

    private fun scheduleRepeat() {
        val next = System.currentTimeMillis() + REPEAT_MS
        if (next <= sp.getLong(R.string.key_phone_alarm_since, 0L) + REPEAT_FOR_MS)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, repeatIntent())
    }

    /**
     * Silence, and Mute 5 min when the phone can honour it, as buttons, and a swipe silences. When the phone's
     * notification has buttons of its own, those replace them, and a swipe presses the first. Posted again on every
     * repeat, so the watch vibrates again: not alert-once on purpose. Our own cancel does not fire the delete intent.
     */
    private fun post(title: String, text: String, canMute: Boolean, actions: List<String>) {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.phone_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = VIBRATION
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(SILENT_CHANNEL_ID, context.getString(R.string.phone_alarm_channel_silent), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION
            }
        )
        val quiet = DoNotDisturbRule.staysQuiet(preferences.get(BooleanKey.AlertOverrideDoNotDisturb), notificationManager.currentInterruptionFilter)
        val builder = NotificationCompat.Builder(context, if (quiet) SILENT_CHANNEL_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (actions.isNotEmpty()) {
            builder.setDeleteIntent(phoneButtonIntent(0))
            actions.forEachIndexed { index, label -> builder.addAction(0, label, phoneButtonIntent(index)) }
        } else {
            builder
                .setDeleteIntent(actionIntent(PhoneAlarmReceiver.ACTION_SILENCE, SILENCE_REQUEST_CODE))
                .addAction(0, context.getString(R.string.phone_alarm_silence), actionIntent(PhoneAlarmReceiver.ACTION_SILENCE, SILENCE_REQUEST_CODE))
            if (canMute)
                builder.addAction(0, context.getString(R.string.phone_alarm_mute, UiInteraction.MUTE_MINUTES), actionIntent(PhoneAlarmReceiver.ACTION_MUTE, MUTE_REQUEST_CODE))
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun repeatIntent(): PendingIntent = actionIntent(PhoneAlarmReceiver.ACTION_REPEAT, REPEAT_REQUEST_CODE)

    /** One request code per button: the code, not the extra, is what keeps their PendingIntents apart. */
    private fun phoneButtonIntent(index: Int): PendingIntent =
        actionIntent(PhoneAlarmReceiver.ACTION_PHONE_BUTTON, PHONE_BUTTON_REQUEST_CODE + index) { putExtra(PhoneAlarmReceiver.EXTRA_INDEX, index) }

    private fun actionIntent(action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, PhoneAlarmReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {

        const val CHANNEL_ID = "AndroidAPS-PhoneAlarm"
        const val SILENT_CHANNEL_ID = "AndroidAPS-PhoneAlarm-silent"
        const val NOTIFICATION_ID = 4730
        const val REPEAT_REQUEST_CODE = 4730
        const val SILENCE_REQUEST_CODE = 4731
        const val MUTE_REQUEST_CODE = 4732

        /** The phone's buttons take this code and the ones after it, one each (a Nightscout alarm has three). */
        const val PHONE_BUTTON_REQUEST_CODE = 4740

        /** Between the button texts in preferences. A button text has no line break. */
        const val ACTIONS_SEPARATOR = "\n"

        /** The notification is posted again this often, for this long after the alarm rang, until it is answered. */
        const val REPEAT_MS = 30_000L
        const val REPEAT_FOR_MS = 5 * 60_000L
        val VIBRATION = longArrayOf(0, 500, 300, 500, 300, 500)
    }
}
