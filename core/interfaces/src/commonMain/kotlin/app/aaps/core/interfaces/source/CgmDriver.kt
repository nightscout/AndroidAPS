package app.aaps.core.interfaces.source

import kotlinx.coroutines.flow.StateFlow

/**
 * A BG source that talks to the sensor itself, as a pump driver talks to the pump, instead of taking the readings
 * from another app. It has its own screen, opened from Manage as the pump's screen is, and from the CGM button
 * (`ElementType.CGM`).
 *
 * A source that only receives values from another app (xDrip, the Dexcom app, ...) is a plain [BgSource]. How either
 * one is calibrated is [BgSource.calibrationMode].
 */
interface CgmDriver : BgSource {

    /**
     * What stops the driver that only the user can fix, or null when nothing does. It puts a "!" on the bottom bar's
     * BG source button, as an uninitialized pump does on the pump's, and the driver's screen shows the reason with the
     * button that fixes it. Not for what the driver recovers from by itself (a lost link, waiting for internet): the
     * stale BG alarm covers that if readings stop.
     */
    val attention: StateFlow<CgmAttention?>
}

/** Why a CGM driver needs the user. */
enum class CgmAttention {

    /** No sensor or transmitter is paired, or the login it needs is missing: the driver cannot start. */
    NOT_PAIRED,

    /** The vendor's server refused the stored login (a changed password, the wrong region): sign in again. */
    SIGN_IN_FAILED
}
