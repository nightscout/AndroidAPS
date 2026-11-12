package app.aaps.plugins.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.aaps.plugins.source.activities.Libre3StatusActivity
import app.aaps.core.ui.R as CoreUiR

/**
 * Keeps the Libre 3 Bluetooth session alive while the screen is off.
 *
 * Why this exists: the only other foreground service in the process is the persistent-notification
 * one, declared `dataSync`. Since the app targets Android 14 the foreground service **type** is what
 * grants a privilege, and `dataSync` grants nothing for Bluetooth: a GATT link owned by no
 * `connectedDevice` service is exactly what an aggressive OEM tears down when the phone goes to
 * sleep. It matters here because the Libre 3 talks on a one-minute cadence that cannot be slowed.
 *
 * The service holds no logic and owns no driver. It exists so the platform sees a `connectedDevice`
 * foreground service for as long as a sensor session is wanted, and it disappears the moment it is
 * not, see [start] and [stop]. Only `Libre3NativePlugin.refreshSessionService` decides that: it runs
 * while a sensor is stored in either slot, production or pre-soak.
 *
 * The notification is separate from [Libre3WarmupNotification]: that one is about warm-up progress
 * and clears at READY, while this one has to stay for the whole session or the service is not a
 * foreground service any more. Both live on the same channel, so the user has one switch.
 */
class Libre3SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14 refuses a foreground service here when the type is not allowed or the
        // Bluetooth permission is missing, and it refuses by throwing. A throw inside
        // onStartCommand is an app crash, on the main thread, while a sensor session is running.
        // Losing the service only costs standby robustness, so the failure is swallowed and the
        // service gives itself up instead of staying started without being in the foreground.
        val started = runCatching {
            createChannel()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        }.isSuccess
        if (!started) stopSelf()
        // The session lives on the driver, not here. If the platform kills us, coming back with a
        // null intent must not be read as "start a session"; the plugin asks again when it needs to.
        return START_NOT_STICKY
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(CoreUiR.drawable.notif_icon)
            .setContentTitle(getString(R.string.libre3_notif_title))
            .setContentText(getString(R.string.libre3_notif_session_alive))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openStatusIntent())
            .build()

    private fun openStatusIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            Intent(this, Libre3StatusActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.libre3_notif_channel_name),
                LIBRE3_SENSOR_STATUS_CHANNEL_IMPORTANCE,
            ).apply { description = getString(R.string.libre3_notif_channel_desc) },
        )
    }

    companion object {

        /** The same channel the warm-up messages use, so the user has one switch for all of them. */
        private const val CHANNEL_ID = Libre3WarmupNotification.CHANNEL_ID

        /** Distinct from the warm-up notification ids (4471 and 4472): this one stays for the session. */
        private const val NOTIFICATION_ID = 4473
        private const val REQUEST_CODE = 4473

        /**
         * Ask the platform for a `connectedDevice` foreground service.
         *
         * Safe to call repeatedly: a second start on a running service only re-posts the same
         * notification. A failure does not throw: a sensor session that cannot get a service is
         * worse off, but it must still run. It is handed back so the caller can log it.
         *
         * @return the failure, or null when the start was asked without one.
         */
        fun start(context: Context): Throwable? =
            runCatching {
                context.startForegroundService(Intent(context, Libre3SessionService::class.java))
            }.exceptionOrNull()

        /** Give the privilege back. Safe when the service is not running. */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, Libre3SessionService::class.java))
            }
        }
    }
}
