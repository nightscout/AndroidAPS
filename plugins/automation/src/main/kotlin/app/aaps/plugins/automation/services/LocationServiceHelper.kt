package app.aaps.plugins.automation.services

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.notifications.NotificationHolder
import javax.inject.Inject
import javax.inject.Singleton

/*
    This code replaces  following
    val intent = Intent(context, LocationService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)

    it fails randomly with error
    Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{e317f7e u0 info.nightscout.nsclient/info.nightscout.androidaps.services.LocationService}

 */
@Singleton
class LocationServiceHelper @Inject constructor(
    private val notificationHolder: NotificationHolder
) {

    fun startService(context: Context) {
        if (!hasLocationPermission(context)) return
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
    }

    fun stopService(context: Context) =
        context.stopService(Intent(context, LocationService::class.java))

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