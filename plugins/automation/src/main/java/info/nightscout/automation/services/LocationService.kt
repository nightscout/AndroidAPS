@file:Suppress("PrivatePropertyName")

package info.nightscout.automation.services

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import dagger.android.DaggerService
import info.nightscout.automation.R
import info.nightscout.automation.events.EventLocationChange
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.NotificationHolder
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class LocationService : DaggerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var notificationHolder: NotificationHolder
    @Inject lateinit var lastLocationDataContainer: LastLocationDataContainer

    private val disposable = CompositeDisposable()
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

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            aapsLogger.debug(LTag.LOCATION, "onStatusChanged: $provider")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        try {
            aapsLogger.debug("Starting LocationService with ID ${notificationHolder.notificationID} notification ${notificationHolder.notification}")
            startForeground(notificationHolder.notificationID, notificationHolder.notification)
        } catch (e: Exception) {
            startForeground(4711, Notification())
        }
        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        try {
            aapsLogger.debug("Starting LocationService with ID ${notificationHolder.notificationID} notification ${notificationHolder.notification}")
            startForeground(notificationHolder.notificationID, notificationHolder.notification)
        } catch (e: Exception) {
            startForeground(4711, Notification())
        }

        // Get last location once until we get regular update
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener {
                lastLocationDataContainer.lastLocation = it
            }
        }

        initializeLocationManager()

        try {
            if (sp.getString(R.string.key_location, "NONE") == "NETWORK") locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_INTERVAL_ACTIVE,
                LOCATION_DISTANCE,
                LocationListener(LocationManager.NETWORK_PROVIDER).also { locationListener = it }
            )
            if (sp.getString(R.string.key_location, "NONE") == "GPS") locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_ACTIVE,
                LOCATION_DISTANCE,
                LocationListener(LocationManager.GPS_PROVIDER).also { locationListener = it }
            )
            if (sp.getString(R.string.key_location, "NONE") == "PASSIVE") locationManager?.requestLocationUpdates(
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
        disposable.add(
            rxBus
                .toObservable(EventAppExit::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({
                               aapsLogger.debug(LTag.LOCATION, "EventAppExit received")
                               stopSelf()
                           }, fabricPrivacy::logException)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            locationListener?.let { locationManager?.removeUpdates(it) }
        } catch (ex: Exception) {
            aapsLogger.error(LTag.LOCATION, "fail to remove location listener, ignore", ex)
        }
        disposable.clear()
    }

    private fun initializeLocationManager() {
        aapsLogger.debug(LTag.LOCATION, "initializeLocationManager - Provider: " + sp.getString(R.string.key_location, "NONE"))
        if (locationManager == null) {
            locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
    }
}