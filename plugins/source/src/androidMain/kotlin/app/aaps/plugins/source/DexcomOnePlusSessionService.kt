package app.aaps.plugins.source

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.objects.workflow.MetroService
import dev.zacsweers.metro.Inject

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
 * It has no notification of its own. A foreground service must show one, and this one shows the
 * app's permanent notification ([NotificationHolder]), as the location service of the automation
 * plugin does. Several foreground services may share one notification. Its own "Keeping the sensor
 * connected" line said nothing the user could act on, and it came back after every app start.
 * What the user needs to know about the sensor is in [DexcomOnePlusWarmupNotification].
 */
class DexcomOnePlusSessionService : MetroService() {

    @Inject lateinit var notificationHolder: NotificationHolder

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            notificationHolder.notificationID,
            notificationHolder.notification,
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

    companion object {

        /**
         * Ask the platform for a `connectedDevice` foreground service.
         *
         * Safe to call repeatedly: a second start on a running service only shows the same
         * notification again. Failures are swallowed on purpose — a sensor session that cannot get a
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
