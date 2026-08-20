package app.aaps.core.interfaces.location

/**
 * Starts and stops whatever keeps the device location up to date for automation's location trigger.
 *
 * Exists so automation does not have to name an Android `Service`. The Android implementation runs a
 * foreground service; an iOS one would use CoreLocation. Both are platform entry points, so neither
 * can live in a multiplatform module.
 *
 * Only a master runs this - a client never executes automations, so it never starts the service.
 */
interface LocationServiceController {

    /**
     * Starts location updates if they are not running already.
     *
     * @return true if the start was issued, false if it was skipped because the location permission
     *   is not granted yet. Callers retry once it is, so a false is normal and not an error.
     */
    fun startService(): Boolean

    /** Stops location updates. Safe to call when nothing is running. */
    fun stopService()
}
