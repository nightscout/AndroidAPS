package app.aaps.plugins.dexcomoneplus

/**
 * Milliseconds on a clock that only moves forward and keeps counting while the phone sleeps.
 *
 * The driver measures two things with it: the age of an advertisement sighting, and the age of the
 * current scan block. Neither may be measured on the wall clock, which the user and the network can
 * move, and neither may be measured on a clock that stops in sleep - a wait that really took minutes
 * would then look like milliseconds and the driver would act on it.
 *
 * On Android this is `SystemClock.elapsedRealtime()`. Only the difference between two readings
 * means anything.
 * `OnePlusScanResult.seenElapsedMs` is a value of this clock and nothing else.
 */
internal expect fun onePlusElapsedMs(): Long
