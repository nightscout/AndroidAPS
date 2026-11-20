package app.aaps.plugins.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.source.activities.Libre3StatusActivity
import app.aaps.plugins.source.activities.Libre3WarmupActivity
import app.aaps.plugins.source.compose.Libre3WarmupCountdown
import app.aaps.plugins.source.compose.toCgmWarmupInfo
import app.aaps.core.ui.R as CoreUiR

/**
 * Importance of the shared Libre 3 sensor-status channel.
 *
 * One value, so a later decision changes it in one place. The promotion alert posts on this
 * existing channel. LOW: these are status messages, not alarms.
 */
internal const val LIBRE3_SENSOR_STATUS_CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_LOW

/**
 * The ongoing status bar message about the sensor.
 *
 * It lets the user leave the warm-up screen and go back to the Overview while the driver keeps
 * working. It shows the honest phase, and it clears itself when the sensor is running or when
 * nothing is going on.
 *
 * One message per slot. Both share the channel, so the user has one switch, but they carry their
 * own id and their own title: a pre-soak that reused the production id would silently overwrite the
 * message about the sensor that feeds the loop.
 *
 * @param slot which sensor this message is about.
 */
class Libre3WarmupNotification(
    private val context: Context,
    private val slot: SensorSlot = SensorSlot.PRODUCTION,
) {

    private val notificationId =
        if (slot == SensorSlot.PRODUCTION) NOTIFICATION_ID_PRODUCTION else NOTIFICATION_ID_STAGING

    private val titleResId =
        if (slot == SensorSlot.PRODUCTION) R.string.libre3_notif_title else R.string.libre3_notif_title_presoak

    init {
        createChannel()
    }

    /** Shows [state], or clears the message when there is nothing to say. */
    fun update(state: Libre3WarmupState) {
        if (state.phase == Libre3WarmupState.Phase.IDLE || state.phase == Libre3WarmupState.Phase.READY) {
            cancel()
            return
        }

        val ongoing = state.phase != Libre3WarmupState.Phase.FAILED
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreUiR.drawable.notif_icon)
            .setContentTitle(context.getString(titleResId))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(openDetailScreen())

        when (state.phase) {
            Libre3WarmupState.Phase.PAIRING      ->
                builder.setContentText(context.getString(R.string.libre3_notif_pairing))
                    .setProgress(0, 0, true)
                    .setShowWhen(false)

            Libre3WarmupState.Phase.CONNECTING   ->
                builder.setContentText(context.getString(R.string.libre3_notif_connecting))
                    .setProgress(0, 0, true)
                    .setShowWhen(false)

            Libre3WarmupState.Phase.RECONNECTING ->
                builder.setContentText(context.getString(R.string.libre3_notif_reconnecting))
                    .setProgress(0, 0, true)
                    .setShowWhen(false)

            Libre3WarmupState.Phase.WARMING      -> {
                builder.setContentTitle(context.getString(R.string.libre3_notif_warming_title))
                    .setContentText(context.getString(R.string.libre3_notif_warming_text))
                val remaining = Libre3WarmupCountdown.remainingMs(state.toCgmWarmupInfo(), System.currentTimeMillis())
                if (remaining != null && remaining > 0L) {
                    // Android counts down by itself, so the message does not have to be rewritten
                    // every second.
                    builder.setWhen(state.endsAtEpochMs ?: (System.currentTimeMillis() + remaining))
                        .setShowWhen(true)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                } else {
                    // Nothing is known, so nothing is promised.
                    builder.setProgress(0, 0, true).setShowWhen(false)
                }
            }

            Libre3WarmupState.Phase.FAILED       ->
                builder.setContentTitle(context.getString(R.string.libre3_notif_failed_title))
                    .setContentText(context.getString(R.string.libre3_notif_failed_text))
                    .setShowWhen(false)

            else                                 -> Unit
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // The user did not allow notifications. The session carries on without one.
        }
    }

    /**
     * One-shot alert. Not ongoing: the user must be able to dismiss it after reading the action.
     *
     * It uses the existing sensor-status channel, at [LIBRE3_SENSOR_STATUS_CHANNEL_IMPORTANCE].
     * Its id is not a warm-up id, so the alert does not replace the countdown.
     */
    fun alert(text: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreUiR.drawable.notif_icon)
            .setContentTitle(context.getString(R.string.libre3_native))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOngoing(false)
        try {
            NotificationManagerCompat.from(context).notify(ALERT_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied. The status screen still shows the promotion result.
        }
    }

    fun cancel() {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {
            // Nothing to clear, or notifications are not allowed.
        }
    }

    /**
     * Where tapping the message goes.
     *
     * The countdown screen for the sensor that feeds the loop. For a pre-soak the status screen,
     * because the soak time and the collected readings live there.
     */
    private fun openDetailScreen(): PendingIntent {
        val target =
            if (slot == SensorSlot.PRODUCTION) Libre3WarmupActivity::class.java
            else Libre3StatusActivity::class.java
        val intent = Intent(context, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            // The request code follows the id, so the two slots never share a pending intent.
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.libre3_notif_channel_name),
            LIBRE3_SENSOR_STATUS_CHANNEL_IMPORTANCE,
        ).apply {
            description = context.getString(R.string.libre3_notif_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {

        /**
         * One channel for everything this plugin says about the sensor, so the user has one switch.
         *
         * `Libre3SessionService` shows its own ongoing message on this same channel and reads the
         * value from here, so there is only one copy of it.
         */
        const val CHANNEL_ID = "libre3_sensor_status"

        /** Unchanged, so an app update does not orphan a live message. */
        private const val NOTIFICATION_ID_PRODUCTION = 4471

        /** The pre-soak's own id: both messages may be on screen at the same time. */
        private const val NOTIFICATION_ID_STAGING = 4472

        /** Promotion problem. Distinct from 4471, 4472 and 4473. */
        private const val ALERT_ID = 4474
    }
}
