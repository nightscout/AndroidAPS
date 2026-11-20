package app.aaps.core.interfaces.source

/**
 * A BG source that talks to the sensor itself, as a pump driver talks to the pump, instead of taking the readings
 * from another app. It has its own screen, opened from Manage as the pump's screen is, and from the CGM button
 * (`ElementType.CGM`).
 *
 * A source that only receives values from another app (xDrip, the Dexcom app, ...) is a plain [BgSource]. How either
 * one is calibrated is [BgSource.calibrationMode].
 */
interface CgmDriver : BgSource
