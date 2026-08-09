package app.aaps.core.data.time

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

/**
 * `secondsFromGMTForDate` is the direct counterpart of `TimeZone.getOffset(timestamp)`: it answers
 * for a given moment, so it accounts for daylight saving at that moment rather than today.
 *
 * The value is milliseconds because that is what the records store, and it has to keep matching the
 * JVM one exactly - see the note on the expect declaration.
 */
actual fun systemUtcOffsetAt(timestamp: Long): Long =
    NSTimeZone.localTimeZone
        .secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0))
        .toLong() * 1000L
