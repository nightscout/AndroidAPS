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
        val reconnect = if (state.phase == OnePlusWarmupState.Phase.RECONNECTING) {
            DexcomOnePlusReconnectNotice.of(state, System.currentTimeMillis())
        } else null
        if (reconnect == DexcomOnePlusReconnectNotice.HIDE) {
            cancel()
            return
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

            OnePlusWarmupState.Phase.RECONNECTING -> when (reconnect) {
                // Between two radio windows of a warm-up: the countdown, as while warming.
                DexcomOnePlusReconnectNotice.WARMUP_COUNTDOWN -> showWarmupCountdown(builder, state)

                // A reconnect that is really a long ADV silence must say so: the field case showed
                // an unchanging "Reconnecting…" for 18 min while nothing was happening.
                DexcomOnePlusReconnectNotice.STALE_MAC        ->
                    builder.setContentTitle(context.getString(R.string.dexcom_oneplus_notif_stale_mac_title))
                        .setContentText(context.getString(R.string.dexcom_oneplus_notif_stale_mac_text))
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText(context.getString(R.string.dexcom_oneplus_notif_stale_mac_text)),
                        )
                        .setProgress(0, 0, true).setShowWhen(false)

                DexcomOnePlusReconnectNotice.NO_SIGNAL        ->
                    builder.setContentText(
                        context.getString(R.string.dexcom_oneplus_notif_waiting_adv, (state.advSilenceMinutes ?: 0L).toInt()),
                    ).setProgress(0, 0, true).setShowWhen(false)

                else                                          ->
                    builder.setContentText(context.getString(R.string.dexcom_oneplus_notif_reconnecting))
                        .setProgress(0, 0, true).setShowWhen(false)
            }

            OnePlusWarmupState.Phase.WARMING      -> showWarmupCountdown(builder, state)

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

    /** The warm-up title and text, with a live countdown when the end is known. */
    private fun showWarmupCountdown(builder: NotificationCompat.Builder, state: OnePlusWarmupState) {
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

/**
 * What the ongoing notification shows for a [OnePlusWarmupState.Phase.RECONNECTING] state.
 *
 * A ONE+ / G7 connects only for a few seconds about every 5 minutes. Between two readings the
 * driver waits for the next radio window and reports RECONNECTING. That is the normal state, so it
 * must not post a notification after every reading. Only a silence that is longer than one reading
 * interval, a sensor that seems to have changed, or a real error is shown. A lost signal also
 * raises its own alarm, see `DexcomOnePlusSensorAlerts`.
 */
internal enum class DexcomOnePlusReconnectNotice {

    /** The normal wait between two readings: no notification. */
    HIDE,

    /** The wait between two radio windows of a warm-up: the warm-up countdown stays. */
    WARMUP_COUNTDOWN,

    /** No signal for [NO_SIGNAL_NOTICE_MINUTES] or more: at least one reading was missed. */
    NO_SIGNAL,

    /** The stored sensor address may be stale (sensor replaced or started elsewhere). */
    STALE_MAC,

    /** A reconnect after an error, or a refusal pause: shown as "Reconnecting". */
    RECONNECTING;

    companion object {

        /** A reading comes about every 5 minutes. One more minute of margin before saying "no signal". */
        const val NO_SIGNAL_NOTICE_MINUTES = 6L

        /** Driver messages of the normal duty cycle, see `OnePlusBleSession`. */
        private val NORMAL_CYCLE_MESSAGES = setOf("egv_loop_exit", "waiting_for_next_adv", "waiting_for_adv")
        private const val CONNECT_ATTEMPT_PREFIX = "connect_attempt_"

        fun of(state: OnePlusWarmupState, nowMs: Long): DexcomOnePlusReconnectNotice {
            val silenceMinutes = state.advSilenceMinutes ?: 0L
            val message = state.message
            val normalCycle = message == null || message in NORMAL_CYCLE_MESSAGES || message.startsWith(CONNECT_ATTEMPT_PREFIX)
            return when {
                state.staleMacSuspected                        -> STALE_MAC
                silenceMinutes >= NO_SIGNAL_NOTICE_MINUTES     -> NO_SIGNAL
                !normalCycle                                   -> RECONNECTING
                (state.endsAtEpochMs ?: 0L) > nowMs            -> WARMUP_COUNTDOWN
                else                                           -> HIDE
            }
        }
    }
}
