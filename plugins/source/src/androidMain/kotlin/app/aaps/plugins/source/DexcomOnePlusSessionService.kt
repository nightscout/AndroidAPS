package app.aaps.plugins.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.aaps.plugins.source.activities.DexcomOnePlusStatusActivity
import app.aaps.core.ui.R as CoreUiR

/**
 * Keeps the Dexcom ONE+ / G7 Bluetooth session alive while the screen is off.
 *
 * Why this exists: the OEM profiles have carried `useForegroundService = true` since they were
 * written, but nothing started a service until this class. The driver ran on whatever else in the
 * app happened to be alive, and the only foreground service in the process is the
 * persistent-notification one, declared `dataSync`. Since the app targets Android 14 the
 * foreground service **type** is what grants a privilege, and `dataSync` grants nothing for
 * Bluetooth: a GATT link owned by no `connectedDevice` service is exactly what an aggressive OEM
 * tears down when the phone goes to sleep.
 *
 * The service holds no logic and owns no driver. It exists so the platform sees a `connectedDevice`
 * foreground service for as long as a sensor session is wanted, and it disappears the moment it is
 * not — see [start] and [stop].
 *
 * The notification is separate from [DexcomOnePlusWarmupNotification]: that one is about warm-up
 * progress and clears at READY, while this one has to stay for the whole session or the service is
 * not a foreground service any more. Both live on the same channel, so the user has one switch.
 */
class DexcomOnePlusSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            },
        )
        // The session lives on the driver, not here. If the platform kills us, coming back with a
        // null intent must not be read as "start a session"; the plugin asks again when it needs to.
        return START_NOT_STICKY
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(CoreUiR.drawable.notif_icon)
            .setContentTitle(getString(R.string.dexcom_oneplus_notif_title))
            .setContentText(getString(R.string.dexcom_oneplus_notif_session_alive))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openStatusIntent())
            .build()

    private fun openStatusIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            Intent(this, DexcomOnePlusStatusActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.dexcom_oneplus_notif_channel_name),
                DEXCOM_ONEPLUS_STATUS_CHANNEL_IMPORTANCE,
            ).apply { description = getString(R.string.dexcom_oneplus_notif_channel_desc) },
        )
    }

    companion object {

        private const val CHANNEL_ID = "DEXCOM_ONEPLUS_STATUS"

        /** Distinct from the warm-up notification id: both may be shown at the same time. */
        private const val NOTIFICATION_ID = 8932
        private const val REQUEST_CODE = 8932

        /**
         * Ask the platform for a `connectedDevice` foreground service.
         *
         * Safe to call repeatedly: a second start on a running service only re-posts the same
         * notification. Failures are swallowed on purpose — a sensor session that cannot get a
         * service is worse off, but it must still run.
         */
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, DexcomOnePlusSessionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** Give the privilege back. Safe when the service is not running. */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DexcomOnePlusSessionService::class.java))
            }
        }
    }
}
