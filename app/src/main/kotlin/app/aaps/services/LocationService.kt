@file:Suppress("PrivatePropertyName")

package app.aaps.services

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.ActivityCompat
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.events.EventLocationChange
import app.aaps.plugins.automation.services.LastLocationDataContainer
import com.google.android.gms.location.LocationServices
import dagger.android.DaggerService
import app.aaps.core.interfaces.rx.collectResilient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

class LocationService : DaggerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var notificationHolder: NotificationHolder
    @Inject lateinit var lastLocationDataContainer: LastLocationDataContainer

    // Replaces the CompositeDisposable: same lifetime, cancelled in onDestroy below.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    private val LOCATION_INTERVAL_ACTIVE = T.mins(5).msecs()
    private val LOCATION_INTERVAL_PASSIVE = T.mins(1).msecs() // this doesn't cost more power

    companion object {

        private const val LOCATION_DISTANCE = 10f
    }

    inner class LocalBinder : Binder() {

        fun getService(): LocationService = this@LocationService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    inner class LocationListener internal constructor(val provider: String) : android.location.LocationListener {

        init {
            aapsLogger.debug(LTag.LOCATION, "LocationListener $provider")
        }

        override fun onLocationChanged(location: Location) {
            aapsLogger.debug(LTag.LOCATION, "onLocationChanged: $location")
            lastLocationDataContainer.lastLocation = location
            rxBus.send(EventLocationChange(location))
        }

        override fun onProviderDisabled(provider: String) {
            aapsLogger.debug(LTag.LOCATION, "onProviderDisabled: $provider")
        }

        override fun onProviderEnabled(provider: String) {
            aapsLogger.debug(LTag.LOCATION, "onProviderEnabled: $provider")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!hasLocationPermission()) {
            aapsLogger.error(LTag.LOCATION, "LocationService started without location permission, stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        aapsLogger.debug("Starting LocationService with ID ${notificationHolder.notificationID} notification ${notificationHolder.notification}")
        // Even with permissions granted, the system can still reject the FGS-location promotion
        // if the app left foreground between startForegroundService() and this onStartCommand
        // delivery and ACCESS_BACKGROUND_LOCATION was revoked in the meantime. Catch the
        // SecurityException so a permission/state race doesn't crash the process.
        try {
            startForeground(notificationHolder.notificationID, notificationHolder.notification)
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.LOCATION, "FGS-location start denied by system, stopping", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun hasLocationPermission(): Boolean {
        val hasFineOrCoarse =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackground =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFineOrCoarse && hasBackground
    }

    override fun onCreate() {
        super.onCreate()

        // Get last location once until we get regular update
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                lastLocationDataContainer.lastLocation = location
                initializeLocationManager()

                try {
                    if (preferences.get(StringKey.AutomationLocation) == "NETWORK") locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        LOCATION_INTERVAL_ACTIVE,
                        LOCATION_DISTANCE,
                        LocationListener(LocationManager.NETWORK_PROVIDER).also { locationListener = it }
                    )
                    if (preferences.get(StringKey.AutomationLocation) == "GPS") locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        LOCATION_INTERVAL_ACTIVE,
                        LOCATION_DISTANCE,
                        LocationListener(LocationManager.GPS_PROVIDER).also { locationListener = it }
                    )
                    if (preferences.get(StringKey.AutomationLocation) == "PASSIVE") locationManager?.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        LOCATION_INTERVAL_PASSIVE,
                        LOCATION_DISTANCE,
                        LocationListener(LocationManager.PASSIVE_PROVIDER).also { locationListener = it }
                    )
                } catch (ex: SecurityException) {
                    aapsLogger.error(LTag.LOCATION, "fail to request location update, ignore", ex)
                } catch (ex: IllegalArgumentException) {
                    aapsLogger.error(LTag.LOCATION, "network provider does not exist", ex)
                }
            }
        } else {
            rxBus.send(EventShowSnackbar(getString(app.aaps.core.ui.R.string.location_permission_not_granted), EventShowSnackbar.Type.Error))
        }

        rxBus.toFlow(EventAppExit::class)
            .collectResilient(scope, aapsLogger, LTag.LOCATION, start = CoroutineStart.UNDISPATCHED) {
                aapsLogger.debug(LTag.LOCATION, "EventAppExit received")
                stopSelf()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            locationListener?.let { locationManager?.removeUpdates(it) }
        } catch (ex: Exception) {
            aapsLogger.error(LTag.LOCATION, "fail to remove location listener, ignore", ex)
        }
    }

    private fun initializeLocationManager() {
        aapsLogger.debug(LTag.LOCATION, "initializeLocationManager - Provider: " + preferences.get(StringKey.AutomationLocation))
        if (locationManager == null) {
            locationManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        }
    }
}