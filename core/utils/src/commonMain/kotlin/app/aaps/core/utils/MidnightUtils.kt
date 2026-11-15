package app.aaps.core.utils

import app.aaps.core.utils.MidnightUtils.millisFrom
import app.aaps.core.utils.MidnightUtils.secondsFrom
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Midnight time conversion
 */
object MidnightUtils {

    /**
     * One local day of one time zone with the same UTC offset from its start to its end.
     *
     * Inside such a day the wall clock time is simply `timestamp + offset`, so it needs no time zone
     * rules. This matters because the IOB calculation asks for the basal rate of every minute of every
     * temporary basal, again for every 5 minute step: millions of calls, and the zone rules were the
     * most expensive app code in a CPU trace.
     *
     * A day where the offset at its start and at its end differ (a DST change) is never stored: it is
     * calculated with the zone rules on every call, as before. A day with the same offset at both ends
     * has no change inside it: checked against the whole tz database, every zone, every day 1970-2045.
     */
    private class Day(val zone: TimeZone, val from: Long, val to: Long, val offsetMillis: Long, val startOfDayMillis: Long) {

        fun contains(zone: TimeZone, timestamp: Long) = timestamp >= from && timestamp < to && zone == this.zone

        /** Wall clock milliseconds of the day, minus those of the day's first instant (see [secondsFrom]). */
        fun millisFromStart(timestamp: Long): Long = (timestamp + offsetMillis).mod(MILLIS_PER_DAY) - startOfDayMillis
    }

    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

    /**
     * The last days used. More than one, because the IOB calculation goes back and forth between today
     * and the days before. A race between threads only costs a lookup, the entries never change.
     */
    private val days = arrayOfNulls<Day>(8)
    private var next = 0

    private fun dayOf(timestamp: Long, tz: TimeZone): Day? {
        for (day in days) if (day != null && day.contains(tz, timestamp)) return day
        val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz).date
        val start = date.atStartOfDayIn(tz)
        val end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
        val offset = tz.offsetAt(start)
        if (offset != tz.offsetAt(Instant.fromEpochMilliseconds(end.toEpochMilliseconds() - 1))) return null
        val day = Day(
            zone = tz,
            from = start.toEpochMilliseconds(),
            to = end.toEpochMilliseconds(),
            offsetMillis = offset.totalSeconds * 1000L,
            startOfDayMillis = start.toLocalDateTime(tz).time.toMillisecondOfDay().toLong()
        )
        days[next] = day
        next = (next + 1) % days.size
        // Not always the day of the timestamp: when the clocks go back over midnight (Newfoundland
        // did this at 00:01), a moment after the start of the next day still has the earlier date.
        return day.takeIf { it.contains(tz, timestamp) }
    }

    /**
     * Wall clock milliseconds of the day of [timestamp], minus those of the day's first instant.
     *
     * The day's first instant is normally 00:00, so this is the time of day. On a day where the clocks
     * jump forward over midnight (Brazil used to do this) local midnight does not exist, and
     * `atStartOfDayIn` gives 01:00 instead - the same thing `atStartOfDay(zone)` did before. Keeping
     * the subtraction preserves the old "ignoring DST change" behaviour exactly rather than assuming
     * the day starts at zero.
     */
    internal fun millisFrom(timestamp: Long, tz: TimeZone): Long {
        dayOf(timestamp, tz)?.let { return it.millisFromStart(timestamp) }
        val local = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz)
        val startOfDay = local.date.atStartOfDayIn(tz).toLocalDateTime(tz)
        return (local.time.toMillisecondOfDay() - startOfDay.time.toMillisecondOfDay()).toLong()
    }

    /** [millisFrom] in whole seconds, as `LocalTime.toSecondOfDay` counts them (the milliseconds are dropped). */
    internal fun secondsFrom(timestamp: Long, tz: TimeZone): Int {
        val millis = millisFrom(timestamp, tz)
        // Both parts of millisFrom are whole seconds apart from the milliseconds of the timestamp itself
        return ((millis - timestamp.mod(1000L)) / 1000).toInt()
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
    fun milliSecFromMidnight(timestamp: Long): Long =
        millisFrom(timestamp, TimeZone.currentSystemDefault())
}
