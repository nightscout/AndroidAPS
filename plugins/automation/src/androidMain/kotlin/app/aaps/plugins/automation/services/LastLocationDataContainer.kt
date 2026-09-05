package app.aaps.plugins.automation.services

import android.location.Location
import app.aaps.plugins.automation.GeoPosition
import app.aaps.plugins.automation.LastKnownLocation
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LastLocationDataContainer @Inject constructor() : LastKnownLocation {

    /** Written by [LocationService]. Stays an Android `Location` because that is what the service gets. */
    var lastLocation: Location? = null

    override fun distanceTo(latitude: Double, longitude: Double): Double? {
        val last = lastLocation ?: return null
        val target = Location("Trigger")
        target.latitude = latitude
        target.longitude = longitude
        return last.distanceTo(target).toDouble()
    }

    override fun position(): GeoPosition? =
        lastLocation?.let { GeoPosition(it.latitude, it.longitude) }
}
