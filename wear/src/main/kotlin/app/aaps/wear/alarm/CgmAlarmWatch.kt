package app.aaps.wear.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The CGM alarms on the watch ([EventData.CgmAlarm]): a notification with an Acknowledge button, and
 * a vibration for as long as asked, so a low at night can wake the wearer. Most come from the phone.
 * With direct-to-watch the watch also raises its own low and urgent low here, through the same path,
 * while the phone is away (see `WatchDirect`).
 *
 * The vibration is marked as an alarm, which the silent mode of the watch does not block, and runs
 * at full strength where the watch lets us set it. The channel itself neither sounds nor vibrates,
 * so the length is exactly the one the phone asked for. The phone sends the alarm again for every
 * reminder, so the watch vibrates again until the alarm is acknowledged.
 *
 * Acknowledge (the button, or a swipe) goes back to the phone ([EventData.CgmAlarmAcknowledge]),
 * which acknowledges the alarm there too. The phone takes the notification down with
 * [EventData.CgmAlarmCancel] when the alarm is over.
 *
 * Adapted from `CgmAlarmWear` in nightscout/AndroidAPS pull request 5397 (commit `bec1b16ba20`), see
 * the NOTICE file of this module.
 */
@SingleIn(AppScope::class)
@Inject
class CgmAlarmWatch(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger
) {

    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator? get() = context.getSystemService(Vibrator::class.java)

    fun show(alarm: EventData.CgmAlarm) {
        aapsLogger.debug(LTag.WEAR, "CGM alarm ${alarm.id}: vibrate ${alarm.vibrationSeconds} s")
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.cgm_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        val acknowledge = acknowledgeIntent(alarm.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(alarm.title)
            .setContentText(alarm.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alarm.message))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setDeleteIntent(acknowledge)
            .addAction(R.drawable.ic_icon_snooze, context.getString(R.string.cgm_alarm_acknowledge), acknowledge)
            .build()
        try {
            notificationManager.notify(notificationId(alarm.id), notification)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.WEAR, "CGM alarm notification not allowed", e)
        }
        vibrate(alarm.vibrationSeconds, alarm.urgent)
    }

    /** The wearer acknowledged [id] here. Stop, remove, and tell the phone. */
    fun acknowledge(id: String) {
        aapsLogger.debug(LTag.WEAR, "CGM alarm acknowledged on the watch: $id")
        remove(id)
        rxBus.send(EventWearToMobile(EventData.CgmAlarmAcknowledge(id)))
    }

    /** The phone says the alarm [id] is over. Our own cancel does not fire the delete intent. */
    fun remove(id: String) {
        notificationManager.cancel(notificationId(id))
        vibrator?.cancel()
    }

    private fun acknowledgeIntent(id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, notificationId(id),
            Intent(context, CgmAlarmReceiver::class.java).setAction(CgmAlarmReceiver.ACTION_ACKNOWLEDGE).putExtra(CgmAlarmReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun vibrate(seconds: Int, urgent: Boolean) {
        val vibrator = vibrator ?: return
        if (seconds <= 0 || !vibrator.hasVibrator()) return
        val timings = pattern(seconds, urgent)
        // Full strength. Without amplitudes the watch uses its default strength, and a low at night
        // has to wake the wearer.
        val effect =
            if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes(timings), -1)
            else VibrationEffect.createWaveform(timings, -1)
        try {
            // USAGE_ALARM: the silent mode of the watch does not block an alarm vibration. The watch app
            // runs from Android 11, and VibrationAttributes exists only from Android 13, so older
            // watches mark the alarm with AudioAttributes. Without this, the call failed there and the
            // alarm did not vibrate at all.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            else
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "CGM alarm vibration failed", e)
        }
    }

    companion object {

        const val CHANNEL_ID = "AndroidAPS-CgmAlarm"

        /** A small fixed range, clear of the ids the other watch notifications use. */
        private const val NOTIFICATION_ID_BASE = 7700

        /** The strongest amplitude [VibrationEffect.createWaveform] accepts (its range is 1..255). */
        const val MAX_AMPLITUDE = 255

        fun notificationId(id: String): Int = NOTIFICATION_ID_BASE + (id.hashCode() and 0xFF)

        /**
         * On and off pulses (ms) that fill [seconds]: long pulses for urgent alarms, shorter ones
         * otherwise. Starts at once (the first value is the wait before the first pulse).
         */
        fun pattern(seconds: Int, urgent: Boolean): LongArray {
            val on = if (urgent) 900L else 600L
            val off = if (urgent) 300L else 400L
            val total = seconds * 1000L
            val timings = mutableListOf(0L)
            var used = 0L
            while (used < total) {
                val pulse = minOf(on, total - used)
                timings += pulse
                used += pulse
                if (used < total) {
                    timings += off
                    used += off
                }
            }
            return timings.toLongArray()
        }

        /** Full strength for each pulse in [timings] (odd positions), off for the gaps. */
        fun amplitudes(timings: LongArray): IntArray = IntArray(timings.size) { if (it % 2 == 1) MAX_AMPLITUDE else 0 }
    }
}
