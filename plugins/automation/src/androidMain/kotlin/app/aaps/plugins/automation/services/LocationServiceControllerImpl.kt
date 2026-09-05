package app.aaps.plugins.automation.services

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.location.LocationServiceController
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.utils.DeferredForegroundStart
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/*
    This code replaces  following
    val intent = Intent(context, LocationService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)

    it fails randomly with error
    Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{e317f7e u0 info.nightscout.nsclient/info.nightscout.androidaps.services.LocationService}

 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class LocationServiceControllerImpl @Inject constructor(
    private val context: Context,
    private val notificationHolder: NotificationHolder
) : LocationServiceController {

    private val deferredStart = DeferredForegroundStart()

    /** Guards [running] against the caller thread racing the deferred main-thread callback. */
    private val lock = AapsLock()

    /** Whether the foreground service is actually up, as opposed to merely wanted. */
    private var running = false

    override fun setLocationUpdatesEnabled(enabled: Boolean) {
        lock.withLock {
            if (enabled) {
                if (running) return@withLock
                // Wait until the process is in foreground: Android 12+ refuses startForegroundService
                // from the background. Re-check under the lock inside the callback, because several
                // reconcile ticks can queue a start before the first one runs.
                deferredStart.start { lock.withLock { if (!running) running = startService() } }
            } else {
                // Cancel first, and unconditionally: a start can still be queued while [running] is
                // false, and it would otherwise fire later and bring the service up after the last
                // location trigger was already gone.
                deferredStart.cancel()
                if (!running) return@withLock
                stopService()
                running = false
            }
        }
    }

    /**
     * @return true if the service start was issued; false if it was skipped because the location
     *   permission isn't granted yet, so [running] stays false and the next enable retries.
     */
    internal fun startService(): Boolean {
        if (!hasLocationPermission(context)) return false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                // The binder of the service that returns the instance that is created.
                val binder = service as LocationService.LocalBinder

                val locationService: LocationService = binder.getService()

                context.startForegroundService(Intent(context, LocationService::class.java))

                // This is the key: Without waiting Android Framework to call this method
                // inside Service.onCreate(), immediately call here to post the notification.
                locationService.startForeground(notificationHolder.notificationID, notificationHolder.notification)

                // Release the connection to prevent leaks.
                context.unbindService(this)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        try {
            context.bindService(Intent(context, LocationService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (_: RuntimeException) {
            // This is probably a broadcast receiver context even though we are calling getApplicationContext().
            // Just call startForegroundService instead since we cannot bind a service to a
            // broadcast receiver context. The service also have to call startForeground in
            // this case.
            context.startForegroundService(Intent(context, LocationService::class.java))
        }
        return true
    }

    internal fun stopService() {
        context.stopService(Intent(context, LocationService::class.java))
    }

    private fun hasLocationPermission(context: Context): Boolean {
        // FGS type=location on Android 14+ (targetSdk 34+) requires the app to either be in
        // the foreground OR have ACCESS_BACKGROUND_LOCATION at the moment startForeground()
        // runs. Because startForegroundService → onStartCommand is async, we can't guarantee
        // the foreground state holds. Require BACKGROUND_LOCATION up-front so we never
        // attempt an FGS-location start that would crash with SecurityException.
        val hasFineOrCoarse =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackground =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFineOrCoarse && hasBackground
    }

}
