package app.aaps.plugins.automation

/**
 * The last position the phone reported.
 *
 * Only the distance calculation is platform specific, so it sits behind this interface and
 * `TriggerLocation` stays plain Kotlin. The platform keeps doing the maths it already did - Android
 * uses `Location.distanceTo`, which works on the WGS84 ellipsoid - so no result changes by moving the
 * rule to shared code.
 */
interface LastKnownLocation {

    /**
     * Distance in meters from the last known position to the given point, or `null` when no position
     * has been reported yet. A trigger that compares against a distance cannot run in that case.
     */
    fun distanceTo(latitude: Double, longitude: Double): Double?

    /** The last reported position, or `null` when none has been reported yet. */
    fun position(): GeoPosition?
}

/** A point on the map, in degrees. */
data class GeoPosition(val latitude: Double, val longitude: Double)
