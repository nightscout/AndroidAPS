package app.aaps.core.utils

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class MidnightUtilsTest {

    @BeforeTest fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
    }

    @Test
    fun secondsFromMidnight() {
        val time = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue((MidnightUtils.secondsFromMidnight(time)) in (0..24 * 3600))
    }

    @Test
    fun testSecondsFromMidnight() {
        val midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(0, MidnightUtils.secondsFromMidnight(midnight))
        val oneHourAfter = LocalDateTime.ofInstant(Instant.ofEpochMilli(midnight), ZoneId.systemDefault()).atZone(ZoneId.systemDefault()).plusHours(1).toInstant().toEpochMilli()
        assertEquals(3600, MidnightUtils.secondsFromMidnight(oneHourAfter))
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
        assertEquals(19 * 3600, MidnightUtils.secondsFromMidnight(beforeSwitch))
        assertEquals(19 * 3600, MidnightUtils.secondsFromMidnight(afterSwitch))
    }

    /**
     * Pins the behaviour described in _docs/IOB_TIME_ZONE.md: the same past moment falls on another time
     * of day after the phone changes its time zone, because the current zone is used, not the zone of
     * that moment. When that bug is fixed, this test has to change with it.
     */
    @Test
    fun aMomentMovesToAnotherTimeOfDayAfterTimeZoneChange() {
        val moment = ZonedDateTime.of(2026, 1, 15, 19, 0, 0, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(19 * 3600, MidnightUtils.secondsFromMidnight(moment))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        assertEquals(18 * 3600, MidnightUtils.secondsFromMidnight(moment))
    }

    @Test
    fun milliSecFromMidnight() {
        val midnight = LocalDate.now().atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(0, MidnightUtils.secondsFromMidnight(midnight))
        val oneHourAfter = LocalDateTime.ofInstant(Instant.ofEpochMilli(midnight), ZoneId.systemDefault()).atZone(ZoneId.systemDefault()).plusHours(1).toInstant().toEpochMilli()
        assertEquals(3600 * 1000, MidnightUtils.milliSecFromMidnight(oneHourAfter))
    }

    @Test fun testDateTimeToDuration() {
        val dateTime = ZonedDateTime.of(1991, 8, 13, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightUtils.secondsFromMidnight(dateTime))
        assertEquals(83101 * 1000L, MidnightUtils.milliSecFromMidnight(dateTime))
    }

    @Test fun testDateTimeToDurationAtDstChange() {
        val dateTime = ZonedDateTime.of(2020, 10, 25, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightUtils.secondsFromMidnight(dateTime))
        assertEquals(83101 * 1000L, MidnightUtils.milliSecFromMidnight(dateTime))
    }

    @Test fun testDateTimeToDurationAtDstReverseChange() {
        val dateTime = ZonedDateTime.of(2020, 3, 29, 23, 5, 1, 0, ZoneId.of("Europe/Amsterdam")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightUtils.secondsFromMidnight(dateTime))
        assertEquals(83101 * 1000L, MidnightUtils.milliSecFromMidnight(dateTime))
    }

    @Test fun testDateTimeInOtherZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals("America/Los_Angeles", ZoneId.systemDefault().id)
        val dateTime = ZonedDateTime.of(2020, 3, 29, 23, 5, 1, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()
        assertEquals(83101, MidnightUtils.secondsFromMidnight(dateTime))
        assertEquals(83101 * 1000L, MidnightUtils.milliSecFromMidnight(dateTime))
    }
}