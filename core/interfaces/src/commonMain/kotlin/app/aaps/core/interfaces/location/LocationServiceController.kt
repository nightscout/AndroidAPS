package app.aaps.core.interfaces.location

/**
 * Turns on and off whatever keeps the device location up to date for automation's location trigger.
 *
 * Exists so automation does not have to name an Android `Service`. The Android implementation runs a
 * foreground service; an iOS one would use CoreLocation. Both are platform entry points, so neither
 * can live in a multiplatform module.
 *
 * Only a master runs this - a client never executes automations, so it never asks for updates.
 */
interface LocationServiceController {

    /**
     * Says whether location updates are wanted right now. Idempotent: callers re-state the same value
     * on every reconcile tick, and repeating a value must do nothing.
     *
     * The implementation owns everything platform-specific about *when* the switch can be honoured.
     * On Android that means two things a caller must not try to do itself:
     *  - the start waits until the process is in foreground, because Android 12+ refuses
     *    `startForegroundService` from the background,
     *  - a start that is refused because the location permission is not granted yet is simply not
     *    latched, so the next `true` retries instead of leaving updates stuck off.
     *
     * So a caller only decides whether it needs a location, never whether the platform is ready.
     */
    fun setLocationUpdatesEnabled(enabled: Boolean)
}
