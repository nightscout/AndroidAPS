package app.aaps.core.interfaces.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

object MidnightTime {

    private fun zone() = TimeZone.currentSystemDefault()

    /**
     * Epoch time of last midnight
     *
     * @return epoch millis
     */
    fun calc(): Long = calc(Clock.System.now().toEpochMilliseconds())

    /**
     * Today's time with 'minutes' from midnight
     *
     * @param minutes minutes to add
     * @return epoch millis of today with hh:mm:00
     */
    fun calcMidnightPlusMinutes(minutes: Int): Long {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        val tz = zone()
        val date = Clock.System.now().toLocalDateTime(tz).date
        return LocalDateTime(date, LocalTime(h, m)).toInstant(tz).toEpochMilliseconds()
    }

    /**
     * Epoch time of last midnight before 'time'
     *
     * @param time time of the day
     * @return epoch millis
     */
    fun calc(time: Long): Long {
        val tz = zone()
        return Instant.fromEpochMilliseconds(time).toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    /**
     * Epoch time of last midnight 'days' back
     *
     * @param daysBack how many days back
     * @return epoch millis of midnight
     */
    fun calcDaysBack(daysBack: Long): Long = calcDaysBack(Clock.System.now().toEpochMilliseconds(), daysBack)

    /**
     * Epoch time of last midnight 'days' back from time
     *
     * @param time start time
     * @param daysBack how many days back
     * @return epoch millis of midnight
     */
    fun calcDaysBack(time: Long, daysBack: Long): Long {
        val tz = zone()
        // Subtract whole calendar days on the DATE, then take the start of that day, so a daylight
        // saving change inside the range shifts the result by the offset rather than by a fixed
        // number of milliseconds.
        val date = Instant.fromEpochMilliseconds(time).toLocalDateTime(tz).date.minus(daysBack.toInt(), DateTimeUnit.DAY)
        return date.atStartOfDayIn(tz).toEpochMilliseconds()
    }
}
