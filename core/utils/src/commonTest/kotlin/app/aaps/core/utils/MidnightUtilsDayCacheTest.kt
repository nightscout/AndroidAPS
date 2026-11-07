package app.aaps.core.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * `MidnightUtils` keeps the last days it used, so most calls need no time zone rules. The answer must
 * stay exactly what the rules give: it is the time of day the basal rate of the profile is read at.
 *
 * Every test compares with [reference], the code before the cache, in zones with unusual rules.
 */
class MidnightUtilsDayCacheTest {

    /** The calculation before the cache, kept as the reference. */
    private fun reference(timestamp: Long, tz: TimeZone): Pair<Long, Int> {
        val local = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz)
        val startOfDay = local.date.atStartOfDayIn(tz).toLocalDateTime(tz)
        return (local.time.toMillisecondOfDay() - startOfDay.time.toMillisecondOfDay()).toLong() to
            local.time.toSecondOfDay() - startOfDay.time.toSecondOfDay()
    }

    private fun assertSame(timestamp: Long, tz: TimeZone) {
        val (millis, seconds) = reference(timestamp, tz)
        assertEquals(millis, MidnightUtils.millisFrom(timestamp, tz), "millis ${tz.id} $timestamp")
        assertEquals(seconds, MidnightUtils.secondsFrom(timestamp, tz), "seconds ${tz.id} $timestamp")
    }

    private val zones = listOf(
        "UTC",
        "Europe/Prague",
        "Europe/Dublin",       // DST written as a negative offset in winter
        "America/Sao_Paulo",   // DST changed at midnight until 2019: some days start at 01:00
        "America/Santiago",
        "America/St_Johns",    // -3:30
        "Asia/Kathmandu",      // +5:45
        "Australia/Lord_Howe", // DST of 30 minutes
        "Pacific/Apia",        // skipped 30 December 2011 completely
        "Africa/Casablanca"    // DST stopped for Ramadan
    ).map { TimeZone.of(it) }

    private fun time(year: Int, month: Int, day: Int) = LocalDateTime(year, month, day, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

    /** In time order, as the IOB calculation mostly asks: the cache is hit nearly always. */
    @Test
    fun sameAnswerInTimeOrderAcrossManyDstChanges() {
        val ranges = listOf(time(2011, 1, 1) to time(2013, 1, 1), time(2018, 1, 1) to time(2020, 1, 1))
        for (tz in zones)
            for ((from, to) in ranges) {
                // 37 minutes and 1,234 ms: hits every hour and minute, and odd milliseconds too
                var t = from
                while (t < to) {
                    assertSame(t, tz)
                    t += 37 * 60_000L + 1_234
                }
            }
    }

    /** Around every change of offset, minute by minute, also just before and after the exact instant. */
    @Test
    fun sameAnswerAroundEachChange() {
        for (tz in zones) {
            var t = time(2010, 1, 1)
            val end = time(2021, 1, 1)
            while (t < end) {
                // Every hour, and minute by minute where the offset changed within that hour
                if (tz.offsetAtMillis(t) != tz.offsetAtMillis(t + 3_600_000L))
                    for (m in -1L..3_600_001L step 59_999L) assertSame(t + m, tz)
                assertSame(t, tz)
                t += 3_600_000L
            }
        }
    }

    /** Random order: most calls miss the cache and fill it again. */
    @Test
    fun sameAnswerInRandomOrder() {
        val random = Random(4711)
        val from = time(2000, 1, 1)
        val to = time(2030, 1, 1)
        repeat(30_000) {
            assertSame(random.nextLong(from, to), zones[random.nextInt(zones.size)])
        }
    }

    /** The same moment in another zone must not get the answer stored for the first one. */
    @Test
    fun aChangedZoneIsNotAnsweredFromTheOtherZonesDay() {
        val t = time(2026, 10, 4) + 13 * 3_600_000L
        for (tz in zones + zones.reversed()) assertSame(t, tz)
        repeat(3) { for (tz in zones) assertSame(t + it, tz) }
    }

    private fun TimeZone.offsetAtMillis(t: Long) = Instant.fromEpochMilliseconds(t).toLocalDateTime(this).let { it.toInstant(TimeZone.UTC).toEpochMilliseconds() - t }
}
