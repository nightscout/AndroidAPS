package app.aaps.core.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Midnight time conversion
 */
object MidnightUtils {

    /**
     * The wall clock offset of the day's first instant.
     *
     * Normally 00:00, so this is zero and every result is simply the time of day. On a day where the
     * clocks jump forward over midnight (Brazil used to do this) local midnight does not exist, and
     * `atStartOfDayIn` gives 01:00 instead - the same thing `atStartOfDay(zone)` did before. Keeping
     * the subtraction preserves the old "ignoring DST change" behaviour exactly rather than assuming
     * the day starts at zero.
     */
    private fun secondsFrom(timestamp: Long, tz: TimeZone): Int {
        val local = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz)
        val startOfDay = local.date.atStartOfDayIn(tz).toLocalDateTime(tz)
        return local.time.toSecondOfDay() - startOfDay.time.toSecondOfDay()
    }

    /**
     * Actual passed seconds from midnight ignoring DST change
     * (thus always having 24 hours in a day, not 23 or 25 in days where DST changes)
     *
     * @return seconds
     */
    fun secondsFromMidnight(): Int =
        secondsFrom(Clock.System.now().toEpochMilliseconds(), TimeZone.currentSystemDefault())

    /**
     * Passed seconds from midnight for specified time ignoring DST change
     * (thus always having 24 hours in a day, not 23 or 25 in days where DST changes)
     *
     * @param timestamp time
     * @return seconds
     */
    fun secondsFromMidnight(timestamp: Long): Int =
        secondsFrom(timestamp, TimeZone.currentSystemDefault())

    /**
     * Passed milliseconds from midnight for specified time ignoring DST change
     * (thus always having 24 hours in a day, not 23 or 25 in days where DST changes)
     *
     * @param timestamp time
     * @return milliseconds
     */
    fun milliSecFromMidnight(timestamp: Long): Long {
        val tz = TimeZone.currentSystemDefault()
        val local = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz)
        val startOfDay = local.date.atStartOfDayIn(tz).toLocalDateTime(tz)
        return (local.time.toMillisecondOfDay() - startOfDay.time.toMillisecondOfDay()).toLong()
    }
}
