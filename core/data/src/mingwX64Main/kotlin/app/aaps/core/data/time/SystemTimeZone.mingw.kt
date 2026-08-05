package app.aaps.core.data.time

/**
 * Experiment only - see [app.aaps.core.data.format.NumberFormatPlatform].
 *
 * A real iOS actual would use `NSTimeZone.localTimeZone.secondsFromGMTForDate`.
 */
actual fun systemUtcOffsetAt(timestamp: Long): Long = 0L
