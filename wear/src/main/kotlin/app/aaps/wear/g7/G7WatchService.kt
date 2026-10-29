package app.aaps.wear.g7

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
import app.aaps.wear.R

/**
 * Keeps the watch app a foreground service of type connectedDevice while it reads the Dexcom G7 itself.
 * That is how Android expects an app that holds a link to a device in the background to run.
 *
 * Not enough on its own on a Samsung watch: there the app is still frozen when the screen dims, unless
 * its battery use is unrestricted (see [G7WatchDirect.isBatteryUnrestricted]). The session itself lives in
 * [G7WatchDirect]; this service only holds the foreground state.
 */
class G7WatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.g7_watch_service_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, G7WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentTitle(getString(R.string.g7_watch_title))
            .setContentText(getString(R.string.g7_watch_state_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        return START_STICKY
    }

    companion object {

        private const val CHANNEL_ID = "AndroidAPS-G7DirectService"

        // Clear of the ids DataLayerListenerServiceWear (1, 2, 556677), CgmAlarmWear (7700..7955) and
        // the G7 notification (7960) use.
        private const val NOTIFICATION_ID = 7961

        fun start(context: Context) {
            context.startForegroundService(Intent(context, G7WatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, G7WatchService::class.java))
        }
    }
}
