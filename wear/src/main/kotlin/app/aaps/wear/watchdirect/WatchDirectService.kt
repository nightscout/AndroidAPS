package app.aaps.wear.watchdirect

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.wear.WearApp
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.comm.ForegroundStart

/**
 * Keeps the watch app a foreground service of type connectedDevice while it reads the sensor itself.
 * That is how Android expects an app that holds a link to a device in the background to run.
 *
 * It has no notification of its own. It shows the watch app's one "AAPS is running" notification,
 * the same one and with the same id as [DataLayerListenerServiceWear], so the wearer sees one
 * persistent notification and not two. What the link is doing is on the watch's own screen
 * ([WatchDirectActivity]), and a notification comes only when the wearer has to do something.
 *
 * Not always enough on its own: some watches still freeze the app when the screen dims unless its
 * battery use is unrestricted, see [WatchDirect.isBatteryUnrestricted]. The link itself lives in
 * [WatchDirect]; this service only holds the foreground state.
 */
class WatchDirectService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The channel this service had for itself before it shared the notification.
        getSystemService(NotificationManager::class.java).deleteNotificationChannel(OLD_CHANNEL_ID)
        // Android can restart this service by itself while the app is in the background, and may then refuse.
        ForegroundStart.attempt(this, (application as WearApp).aapsLogger) {
            ServiceCompat.startForeground(
                this,
                DataLayerListenerServiceWear.FOREGROUND_NOTIF_ID,
                DataLayerListenerServiceWear.foregroundNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        }
        return START_STICKY
    }

    companion object {

        private const val OLD_CHANNEL_ID = "AndroidAPS-WatchDirectService"

        /** @param retryWhenRefused see [ForegroundStart.attempt]. */
        fun start(context: Context, aapsLogger: AAPSLogger, retryWhenRefused: Boolean = true) {
            ForegroundStart.startService(context, Intent(context, WatchDirectService::class.java), aapsLogger, retryWhenRefused)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchDirectService::class.java))
        }
    }
}
