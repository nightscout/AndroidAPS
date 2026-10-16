package app.aaps.persistentNotification

import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationHolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/*
    This code replaces  following
    val alarm = Intent(context, DummyService::class.java)
    alarm.putExtra("soundId", n.soundId)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(alarm) else context.startService(alarm)

    it fails randomly with error
    Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{e317f7e u0 info.nightscout.nsclient/info.nightscout.androidaps.services.DummyService}

 */
@SingleIn(AppScope::class)
@Inject
class DummyServiceHelper(
    private val notificationHolder: NotificationHolder,
    private val aapsLogger: AAPSLogger
) {

    /**
     * Whether Android lets this app start a foreground service while it is in the background.
     *
     * Android 12+ refuses that in general, with a list of exemptions. The one AAPS relies on is the
     * battery optimisation exemption, which the setup wizard asks for. A foreground service of type
     * `specialUse`, which DummyService is, has no further "while in use" rule, so the exemption is
     * enough. A location service is different: it may only start while the app is on screen.
     */
    fun mayStartFromBackground(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Starts DummyService and puts it into the foreground.
     *
     * [onNotAllowed] runs when Android refuses the foreground service now, so the caller can try again
     * later. Without that the refusal would be a throw on the main thread, and the app would end.
     */
    fun startService(context: Context, onNotAllowed: () -> Unit = {}) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                // The binder of the service that returns the instance that is created.
                val binder: DummyService.LocalBinder = service as DummyService.LocalBinder

                val dummyService: DummyService = binder.getService()

                try {
                    context.startForegroundService(Intent(context, DummyService::class.java))

                    // This is the key: Without waiting Android Framework to call this method
                    // inside Service.onCreate(), immediately call here to post the notification.
                    dummyService.startForeground(notificationHolder.notificationID, notificationHolder.notification)
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    notAllowed(e, onNotAllowed)
                } finally {
                    // Release the connection to prevent leaks.
                    context.unbindService(this)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        try {
            context.bindService(Intent(context, DummyService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (ignored: RuntimeException) {
            // This is probably a broadcast receiver context even though we are calling getApplicationContext().
            // Just call startForegroundService instead since we cannot bind a service to a
            // broadcast receiver context. The service also have to call startForeground in
            // this case.
            try {
                context.startForegroundService(Intent(context, DummyService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                notAllowed(e, onNotAllowed)
            }
        }
    }

    fun stopService(context: Context) {
        context.stopService(Intent(context, DummyService::class.java))
    }

    private fun notAllowed(e: ForegroundServiceStartNotAllowedException, onNotAllowed: () -> Unit) {
        aapsLogger.warn(LTag.CORE, "Android does not allow DummyService into the foreground now: ${e.message}")
        onNotAllowed()
    }
}
