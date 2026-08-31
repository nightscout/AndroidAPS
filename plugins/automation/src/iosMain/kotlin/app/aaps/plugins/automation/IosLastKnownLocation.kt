package app.aaps.plugins.automation

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager

/**
 * The last position iOS reported, through Core Location.
 *
 * The distance is computed by `CLLocation.distanceFromLocation`, not by a formula written here, for
 * the same reason the Android side calls `Location.distanceTo`: the platform does the geodesic maths
 * on the WGS84 ellipsoid, and a hand rolled haversine would disagree by enough to move a trigger's
 * edge. Only the decision is shared; the calculation stays where it was.
 *
 * ## Permission
 *
 * `location` is null until the user has allowed location access **and** something has asked iOS for
 * a fix. Authorization is requested lazily rather than in the constructor, so building the graph
 * never puts a system prompt in front of the user. A trigger that runs before a fix arrives sees
 * null, which the interface already defines as "cannot run".
 *
 * The app's `Info.plist` must carry `NSLocationWhenInUseUsageDescription`; iOS terminates an app
 * that asks for location without one.
 *
 * Updates start on the first call and are not stopped again, which mirrors the Android side, where
 * `LocationService` runs for as long as location automation is set up. Because the manager is lazy,
 * a build with no location trigger never starts it and pays nothing.
 */
@OptIn(ExperimentalForeignApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class IosLastKnownLocation @Inject constructor(
    private val aapsLogger: AAPSLogger
) : LastKnownLocation {

    private val manager by lazy {
        CLLocationManager().also {
            it.requestWhenInUseAuthorization()
            it.startUpdatingLocation()
        }
    }

    override fun distanceTo(latitude: Double, longitude: Double): Double? {
        val last = manager.location ?: return noFix()
        return last.distanceFromLocation(CLLocation(latitude = latitude, longitude = longitude))
    }

    override fun position(): GeoPosition? {
        val last = manager.location ?: return noFix()
        return last.toGeoPosition()
    }

    private fun <T> noFix(): T? {
        aapsLogger.debug(LTag.AUTOMATION, "No location fix yet, a location trigger cannot run")
        return null
    }
}

/**
 * A Core Location fix as AAPS's own point type.
 *
 * Its own function so it can be tested: `CLLocationCoordinate2D` is a C struct read through
 * `useContents`, and the two fields are both doubles, so swapping them compiles perfectly and puts
 * every location trigger somewhere else on earth.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun CLLocation.toGeoPosition(): GeoPosition =
    coordinate.useContents { GeoPosition(latitude = latitude, longitude = longitude) }
