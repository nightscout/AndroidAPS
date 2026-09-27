package app.aaps.wear.comm

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
import androidx.core.app.NotificationManagerCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * CGM alarms from the phone ([EventData.CgmAlarm]): a notification with an Acknowledge button, and a
 * vibration for as long as the phone asks, so a low at night can wake the wearer.
 *
 * The vibration is marked as an alarm, which the watch's silent setting does not block, and runs at
 * the motor's full strength where the watch lets us set it. The channel
 * itself neither sounds nor vibrates, so the length is exactly the one chosen on the phone.
 *
 * Acknowledge (the button, or swiping the notification away) goes back to the phone, which then
 * acknowledges the alarm there too.
 */
@SingleIn(AppScope::class)
@Inject
class CgmAlarmWear(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val vibrator: Vibrator? get() = context.getSystemService(Vibrator::class.java)

    init {
        rxBus.toFlow(EventData.CgmAlarm::class).collectResilient(scope, aapsLogger, LTag.WEAR) { show(it) }
        rxBus.toFlow(EventData.CgmAlarmCancel::class).collectResilient(scope, aapsLogger, LTag.WEAR) { remove(it.id) }
    }

    /** The wearer acknowledged [id] here. Stop, remove, and tell the phone. */
    fun acknowledge(id: String) {
        aapsLogger.debug(LTag.WEAR, "CGM alarm acknowledged on the watch: $id")
        remove(id)
        rxBus.send(EventWearToMobile(EventData.CgmAlarmAcknowledge(id)))
    }

    private fun show(alarm: EventData.CgmAlarm) {
        aapsLogger.debug(LTag.WEAR, "CGM alarm ${alarm.id}: vibrate ${alarm.vibrationSeconds} s")
        createChannel()
        val acknowledge = PendingIntent.getService(
            context, notificationId(alarm.id),
            Intent(context, DataLayerListenerServiceWear::class.java)
                .setAction(DataLayerListenerServiceWear.INTENT_CGM_ALARM_ACKNOWLEDGE)
                .putExtra(DataLayerListenerServiceWear.KEY_CGM_ALARM_ID, alarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(alarm.title)
            .setContentText(alarm.message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setDeleteIntent(acknowledge)
            .addAction(R.drawable.ic_icon_snooze, context.getString(R.string.cgm_alarm_acknowledge), acknowledge)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(alarm.id), notification)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.WEAR, "CGM alarm notification not allowed", e)
        }
        vibrate(alarm.vibrationSeconds, alarm.urgent)
    }

    private fun remove(id: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(id))
        vibrator?.cancel()
    }

    private fun vibrate(seconds: Int, urgent: Boolean) {
        val vibrator = vibrator ?: return
        if (seconds <= 0 || !vibrator.hasVibrator()) return
        val timings = pattern(seconds, urgent)
        // Full strength. Without amplitudes the watch uses its default strength, well below what a
        // strong motor (Galaxy Watch Ultra) can do, and a low at night has to wake the wearer.
        val effect =
            if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes(timings), -1)
            else VibrationEffect.createWaveform(timings, -1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "CGM alarm vibration failed", e)
        }
    }

    private fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.cgm_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    // A small fixed range, clear of the ids DataLayerListenerServiceWear uses (1, 2, 556677).
    private fun notificationId(id: String): Int = NOTIFICATION_ID_BASE + (id.hashCode() and 0xFF)

    companion object {

        const val CHANNEL_ID = "AndroidAPS-CgmAlarm"
        private const val NOTIFICATION_ID_BASE = 7700

        /** The strongest amplitude [VibrationEffect.createWaveform] accepts (its range is 1..255). */
        const val MAX_AMPLITUDE = 255

        /**
         * On/off pulses (ms) that fill [seconds]: long pulses for urgent alarms, shorter ones otherwise.
         * Starts at once (the first value is the wait before the first pulse).
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

        /** Maximum strength for each pulse in [timings] (odd positions), off for the gaps. */
        fun amplitudes(timings: LongArray): IntArray =
            IntArray(timings.size) { if (it % 2 == 1) MAX_AMPLITUDE else 0 }
    }
}
