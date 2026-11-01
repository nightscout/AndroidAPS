package app.aaps.plugins.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.source.activities.DexcomOnePlusWarmupActivity
import app.aaps.plugins.source.compose.DexcomOnePlusWarmupCountdown
import app.aaps.plugins.source.compose.toCgmWarmupInfo
import app.aaps.core.ui.R as CoreUiR

/** Importance of the shared ONE+ status channel. One value, so it can be changed in one place. */
internal const val DEXCOM_ONEPLUS_STATUS_CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_LOW

/**
 * Ongoing status-bar notification mirroring the live Dexcom ONE+ session / warm-up state
 * ([OnePlusWarmupState]). It lets the user leave the warm-up screen and the app while the driver
 * session keeps running in the background: the notification reflects the honest phase
 * (connecting / reconnecting / warming) with a live countdown, and clears at READY / IDLE.
 *
 * Plugin-owned; touches no shared module.
 *
 * NOTE: [CoreUiR.drawable.notif_icon] is the shared status-bar glyph.
 */
class DexcomOnePlusWarmupNotification(private val context: Context) {

    init {
        createChannel()
    }

    /** Reflect [state] into the ongoing notification, or clear it when there is nothing to show. */
    fun update(state: OnePlusWarmupState) {
        when (state.phase) {
            OnePlusWarmupState.Phase.IDLE,
            OnePlusWarmupState.Phase.READY -> {
                cancel()
                return
            }

            else                           -> Unit
        }

        val ongoing = state.phase != OnePlusWarmupState.Phase.FAILED
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreUiR.drawable.notif_icon)
            .setContentTitle(context.getString(R.string.dexcom_oneplus_notif_title))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(openDetailIntent())

        when (state.phase) {
            OnePlusWarmupState.Phase.PAIRING      ->
                builder.setContentText(context.getString(R.string.dexcom_oneplus_notif_pairing))
                    .setProgress(0, 0, true)
                    .setShowWhen(false)

            OnePlusWarmupState.Phase.CONNECTING   ->
                builder.setContentText(context.getString(R.string.dexcom_oneplus_notif_connecting))
                    .setProgress(0, 0, true)
                    .setShowWhen(false)

            OnePlusWarmupState.Phase.RECONNECTING -> {
                // A reconnect that is really a long ADV silence must say so: the field case showed
                // an unchanging "Reconnecting…" for 18 min while nothing was happening.
                val silenceMinutes = state.advSilenceMinutes
                when {
                    state.staleMacSuspected                      ->
                        builder.setContentTitle(context.getString(R.string.dexcom_oneplus_notif_stale_mac_title))
                            .setContentText(context.getString(R.string.dexcom_oneplus_notif_stale_mac_text))
                            .setStyle(
                                NotificationCompat.BigTextStyle()
                                    .bigText(context.getString(R.string.dexcom_oneplus_notif_stale_mac_text)),
                            )

                    silenceMinutes != null && silenceMinutes > 0 ->
                        builder.setContentText(
                            context.getString(
                                R.string.dexcom_oneplus_notif_waiting_adv,
                                silenceMinutes.toInt(),
                            ),
                        )

                    else                                         ->
                        builder.setContentText(context.getString(R.string.dexcom_oneplus_notif_reconnecting))
                }
                builder.setProgress(0, 0, true).setShowWhen(false)
            }

            OnePlusWarmupState.Phase.WARMING      -> {
                builder.setContentTitle(context.getString(R.string.dexcom_oneplus_notif_warming_title))
                    .setContentText(context.getString(R.string.dexcom_oneplus_notif_warming_text))
                val remaining = DexcomOnePlusWarmupCountdown.resolveRemainingMs(
                    state = state.toCgmWarmupInfo(),
                    nowEpochMs = System.currentTimeMillis(),
                    localFallbackEndsAtEpochMs = null,
                )
                if (remaining != null && remaining > 0L) {
                    // Live status-bar countdown to end — no per-second updates needed.
                    val endsAt = state.endsAtEpochMs ?: (System.currentTimeMillis() + remaining)
                    builder.setWhen(endsAt)
                        .setShowWhen(true)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                } else {
                    builder.setProgress(0, 0, true).setShowWhen(false)
                }
            }

            OnePlusWarmupState.Phase.FAILED       ->
                builder.setContentTitle(context.getString(R.string.dexcom_oneplus_notif_failed_title))
                    .setContentText(context.getString(R.string.dexcom_oneplus_notif_failed_text))
                    .setShowWhen(false)

            else                                  -> Unit
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+ — nothing to show, session continues.
        }
    }

    /** One-shot alert. Not ongoing: the user must be able to dismiss it after reading the action. */
    fun alert(text: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreUiR.drawable.notif_icon)
            .setContentTitle(context.getString(R.string.dexcom_oneplus_native))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOngoing(false)
        try {
            NotificationManagerCompat.from(context).notify(ALERT_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — the promotion result on the status screen still says it.
        }
    }

    fun cancel() {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Throwable) {
        }
    }

    private fun openDetailIntent(): PendingIntent {
        val intent = Intent(context, DexcomOnePlusWarmupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.dexcom_oneplus_notif_channel_name),
            DEXCOM_ONEPLUS_STATUS_CHANNEL_IMPORTANCE,
        ).apply {
            description = context.getString(R.string.dexcom_oneplus_notif_channel_desc)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "DEXCOM_ONEPLUS_STATUS"
        private const val NOTIFICATION_ID = 8931

        /** Not 8931 (warm-up) and not 8932 (session foreground service): those replace each other. */
        private const val ALERT_ID = 8933
    }
}
