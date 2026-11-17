package app.aaps.core.objects.interfaces.utils

import app.aaps.core.interfaces.utils.MidnightTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone as KotlinxTimeZone

/**
 * How far into the local day a moment is: the [MidnightTime.secondsFromMidnight] and
 * [MidnightTime.millisFromMidnight] half of the object. The other half - where the day starts - is in
 * [MidnightTimeTest] and [MidnightTimeParityTest], and the day cache behind both is in
 * [MidnightTimeDayCacheTest].
 */
class MidnightTimeOfDayTest {

    @BeforeTest fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
    }

    /**
     * The zone every call below passes, read fresh each time.
     *
     * The alias is needed because this file also uses `java.util.TimeZone` to move the default zone.
     * Reading it per call is what keeps the "moved the zone, see it at once" cases below honest - the
     * app passes `SystemTimeZone.current()` instead, which keeps its last read, and [MidnightTime]
     * does not care which of the two it is given.
     */
    private fun zone() = KotlinxTimeZone.currentSystemDefault()

    @Test
    fun secondsFromMidnight() {
        val time = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue((MidnightTime.secondsFromMidnight(time, zone())) in (0..24 * 3600))
    }

    @Test
    fun testSecondsFromMidnight() {
        val midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(0, MidnightTime.secondsFromMidnight(midnight, zone()))
        val oneHourAfter = LocalDateTime.ofInstant(Instant.ofEpochMilli(midnight), ZoneId.systemDefault()).atZone(ZoneId.systemDefault()).plusHours(1).toInstant().toEpochMilli()
        assertEquals(3600, MidnightTime.secondsFromMidnight(oneHourAfter, zone()))
    }

    /**
     * The zone rules give every moment its own offset, so a moment before and one after a DST switch
     * both keep their local time. The basal profile of the past therefore does not move at a DST
     * switch, and the IOB cache needs no reset for it.
     */
    @Test
    fun aMomentKeepsItsTimeOfDayAcrossDstSwitch() {
        val beforeSwitch = ZonedDateTime.of(2026, 3, 28, 19, 0, 0, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli() // UTC+1
        val afterSwitch = ZonedDateTime.of(2026, 3, 30, 19, 0, 0, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli() // UTC+2
        assertEquals(19 * 3600, MidnightTime.secondsFromMidnight(beforeSwitch, zone()))
        assertEquals(19 * 3600, MidnightTime.secondsFromMidnight(afterSwitch, zone()))
    }

    /**
     * Pins the behaviour described in _docs/IOB_TIME_ZONE.md: the same past moment falls on another time
     * of day after the phone changes its time zone, because the current zone is used, not the zone of
     * that moment. When that bug is fixed, this test has to change with it.
     */
    @Test
    fun aMomentMovesToAnotherTimeOfDayAfterTimeZoneChange() {
        val moment = ZonedDateTime.of(2026, 1, 15, 19, 0, 0, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(19 * 3600, MidnightTime.secondsFromMidnight(moment, zone()))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        assertEquals(18 * 3600, MidnightTime.secondsFromMidnight(moment, zone()))
    }

    @Test
    fun milliSecFromMidnight() {
        val midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(0, MidnightTime.secondsFromMidnight(midnight, zone()))
        val oneHourAfter = LocalDateTime.ofInstant(Instant.ofEpochMilli(midnight), ZoneId.systemDefault()).atZone(ZoneId.systemDefault()).plusHours(1).toInstant().toEpochMilli()
        assertEquals(3600 * 1000, MidnightTime.millisFromMidnight(oneHourAfter, zone()))
    }

    @Test fun testDateTimeToDuration() {
        val dateTime = ZonedDateTime.of(1991, 8, 13, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightTime.secondsFromMidnight(dateTime, zone()))
        assertEquals(83101 * 1000L, MidnightTime.millisFromMidnight(dateTime, zone()))
    }

    @Test fun testDateTimeToDurationAtDstChange() {
        val dateTime = ZonedDateTime.of(2020, 10, 25, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightTime.secondsFromMidnight(dateTime, zone()))
        assertEquals(83101 * 1000L, MidnightTime.millisFromMidnight(dateTime, zone()))
    }

    @Test fun testDateTimeToDurationAtDstReverseChange() {
        val dateTime = ZonedDateTime.of(2020, 3, 29, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightTime.secondsFromMidnight(dateTime, zone()))
        assertEquals(83101 * 1000L, MidnightTime.millisFromMidnight(dateTime, zone()))
    }

    @Test fun testDateTimeInOtherZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals("America/Los_Angeles", ZoneId.systemDefault().id)
        val dateTime = ZonedDateTime.of(2020, 3, 29, 23, 5, 1, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightTime.secondsFromMidnight(dateTime, zone()))
        assertEquals(83101 * 1000L, MidnightTime.millisFromMidnight(dateTime, zone()))
    }
}