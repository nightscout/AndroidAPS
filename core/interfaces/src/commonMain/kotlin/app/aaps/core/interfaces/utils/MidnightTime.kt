package app.aaps.core.interfaces.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The local day: where it starts, and how far into it a moment is.
 *
 * Both questions used to have their own object - `MidnightTime` answered the first and
 * `MidnightUtils` in `:core:utils` the second - which left two names for one idea and no way to tell
 * from the name which did what. They are one object now.
 *
 * - where the day starts, as epoch milliseconds: [last], [before], [daysBack], [plusMinutes]
 * - how far into the day a moment is, as a duration: [secondsFromMidnight], [millisFromMidnight]
 *
 * The time zone is a parameter and is never read here. [secondsFromMidnight] is asked for the basal
 * rate of every minute of every temporary basal, again for every 5 minute step, so it runs millions
 * of times per IOB calculation - and reading the system zone costs a file read per call on Apple
 * targets. The app passes `SystemTimeZone.current()`, which keeps its last read. Deliberately
 * without a default value: a default would be evaluated in this module, which would quietly put that
 * per-call read back for every caller that left the argument out.
 */
object MidnightTime {

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

        /** Wall clock milliseconds of the day, minus those of the day's first instant (see [secondsFromMidnight]). */
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

    // ---- where the day starts -------------------------------------------------------------------

    /**
     * Epoch time of the last midnight
     *
     * @param tz time zone whose day it is
     * @return epoch millis
     */
    fun last(tz: TimeZone): Long = before(Clock.System.now().toEpochMilliseconds(), tz)

    /**
     * Epoch time of the midnight before [timestamp]
     *
     * @param timestamp time of the day
     * @param tz time zone whose day it is
     * @return epoch millis
     */
    fun before(timestamp: Long, tz: TimeZone): Long =
        Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()

    /**
     * Today's time with [minutes] from midnight
     *
     * @param minutes minutes to add
     * @param tz time zone whose day it is
     * @return epoch millis of today with hh:mm:00
     */
    fun plusMinutes(minutes: Int, tz: TimeZone): Long {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        val date = Clock.System.now().toLocalDateTime(tz).date
        return LocalDateTime(date, LocalTime(h, m)).toInstant(tz).toEpochMilliseconds()
    }

    /**
     * Epoch time of the midnight [daysBack] days back
     *
     * @param daysBack how many days back
     * @param tz time zone whose day it is
     * @return epoch millis of midnight
     */
    fun daysBack(daysBack: Long, tz: TimeZone): Long = daysBack(Clock.System.now().toEpochMilliseconds(), daysBack, tz)

    /**
     * Epoch time of the midnight [daysBack] days back from [timestamp]
     *
     * @param timestamp start time
     * @param daysBack how many days back
     * @param tz time zone whose day it is
     * @return epoch millis of midnight
     */
    fun daysBack(timestamp: Long, daysBack: Long, tz: TimeZone): Long {
        // Subtract whole calendar days on the DATE, then take the start of that day, so a daylight
        // saving change inside the range shifts the result by the offset rather than by a fixed
        // number of milliseconds.
        val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz).date.minus(daysBack.toInt(), DateTimeUnit.DAY)
        return date.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    // ---- how far into the day ------------------------------------------------------------------

    /**
     * Actual passed seconds from midnight ignoring DST change
     * (thus always having 24 hours in a day, not 23 or 25 in days where DST changes)
     *
     * @param tz time zone whose day it is
     * @return seconds
     */
    fun secondsFromMidnight(tz: TimeZone): Int = secondsFromMidnight(Clock.System.now().toEpochMilliseconds(), tz)

    /**
     * Passed seconds from midnight for specified time ignoring DST change
     * (thus always having 24 hours in a day, not 23 or 25 in days where DST changes)
     *
     * As `LocalTime.toSecondOfDay` counts them: the milliseconds are dropped.
     *
     * @param timestamp time
     * @param tz time zone whose day it is
     * @return seconds
     */
    fun secondsFromMidnight(timestamp: Long, tz: TimeZone): Int {
        val millis = millisFromMidnight(timestamp, tz)
        // Both parts of millisFromMidnight are whole seconds apart from the milliseconds of the timestamp itself
        return ((millis - timestamp.mod(1000L)) / 1000).toInt()
    }

    /**
     * Passed milliseconds from midnight for specified time ignoring DST change
     * (thus always having 24 hours in a day, not 23 or 25 in days where DST changes)
     *
     * The day's first instant is normally 00:00, so this is the time of day. On a day where the clocks
     * jump forward over midnight (Brazil used to do this) local midnight does not exist, and
     * `atStartOfDayIn` gives 01:00 instead - the same thing `atStartOfDay(zone)` did before. Keeping
     * the subtraction preserves the old "ignoring DST change" behaviour exactly rather than assuming
     * the day starts at zero.
     *
     * @param timestamp time
     * @param tz time zone whose day it is
     * @return milliseconds
     */
    fun millisFromMidnight(timestamp: Long, tz: TimeZone): Long {
        dayOf(timestamp, tz)?.let { return it.millisFromStart(timestamp) }
        val local = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz)
        val startOfDay = local.date.atStartOfDayIn(tz).toLocalDateTime(tz)
        return (local.time.toMillisecondOfDay() - startOfDay.time.toMillisecondOfDay()).toLong()
    }
}
