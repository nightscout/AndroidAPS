package app.aaps.wear.watchdirect

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
import app.aaps.wear.R

/**
 * Keeps the watch app a foreground service of type connectedDevice while it reads the sensor itself.
 * That is how Android expects an app that holds a link to a device in the background to run.
 *
 * Not always enough on its own: some watches still freeze the app when the screen dims unless its
 * battery use is unrestricted, see [WatchDirect.isBatteryUnrestricted]. The link itself lives in
 * [WatchDirect]; this service only holds the foreground state.
 */
class WatchDirectService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.watch_direct_service_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, WatchDirectActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(getString(R.string.watch_direct_title))
            .setContentText(getString(R.string.watch_direct_state_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_STICKY
    }

    companion object {

        private const val CHANNEL_ID = "AndroidAPS-WatchDirectService"

        // Clear of the ids DataLayerListenerServiceWear (1, 2, 556677) and the other watch notifications use.
        private const val NOTIFICATION_ID = 7971

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WatchDirectService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchDirectService::class.java))
        }
    }
}
