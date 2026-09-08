package app.aaps.plugins.automation

import app.aaps.core.interfaces.location.LocationServiceController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager

/**
 * Turns location updates on and off through Core Location.
 *
 * Android needs a foreground service for this and has to wait for the process to be foregrounded
 * before starting it. iOS has no such dance: `startUpdatingLocation` is a method call, and the
 * system prompts for permission the first time.
 *
 * Idempotent as the interface requires - callers re-state the same value on every reconcile tick,
 * and repeating it must do nothing. That matters more here than it looks: calling
 * `startUpdatingLocation` repeatedly is not free, and stopping and restarting would discard the
 * accuracy the system has built up.
 *
 * ## Permission
 *
 * A start that happens before the user has allowed location is **not latched**, matching the Android
 * contract: the next `true` retries rather than leaving updates stuck off. iOS answers
 * `authorizationStatus` synchronously, so this can tell the two cases apart instead of guessing.
 *
 * The app's `Info.plist` must carry `NSLocationWhenInUseUsageDescription`; iOS terminates an app
 * that asks for location without one.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosLocationServiceController @Inject constructor(
    private val aapsLogger: AAPSLogger
) : LocationServiceController {

    private val manager by lazy { CLLocationManager() }

    /** What the platform is actually doing, which is not the same as what was last asked for. */
    private var updating = false

    override fun setLocationUpdatesEnabled(enabled: Boolean) {
        if (enabled == updating) return

        if (enabled) {
            manager.requestWhenInUseAuthorization()
            manager.startUpdatingLocation()
            updating = true
            aapsLogger.debug(LTag.AUTOMATION, "Location updates started")
        } else {
            manager.stopUpdatingLocation()
            updating = false
            aapsLogger.debug(LTag.AUTOMATION, "Location updates stopped")
        }
    }

    /**
     * The last fix, or null when nothing has asked for updates.
     *
     * Null rather than the system's cached location, deliberately: on Android the reader is
     * `LastLocationDataContainer`, a passive holder that `LocationService` writes into, so it is
     * empty until automation actually needs a location. Answering from a stale cache here would make
     * the two platforms disagree about what "no fix" means.
     *
     * This lives with the manager rather than beside the reader because there must be exactly one
     * `CLLocationManager`. [IosLastKnownLocation] used to keep a second one and start it from a
     * `by lazy` on first read, so merely opening the trigger editor began location updates that
     * nothing could stop - and on this platform the automation runtime is never started, so no
     * location trigger can fire and the updates bought nothing at all.
     */
    internal fun lastLocation(): CLLocation? = if (updating) manager.location else null
}
