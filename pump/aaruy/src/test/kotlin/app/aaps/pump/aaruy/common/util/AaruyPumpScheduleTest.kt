package app.aaps.pump.aaruy.common.util

import app.aaps.pump.aaruy.comm.BasalItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class AaruyPumpScheduleTest {

    private val winter = 1_793_830_000_000L // 2026-11-04
    private val summer = 1_782_000_000_000L // 2026-06-21
    private val prague = TimeZone.getTimeZone("Europe/Prague")

    @Test
    fun thePumpIsSevenHoursAheadOfPragueInWinterAndSixInSummer() {
        assertEquals(14, AaruyPumpSchedule.shift(winter, prague))
        assertEquals(12, AaruyPumpSchedule.shift(summer, prague))
    }

    @Test
    fun aPumpInChinaNeedsNoShift() {
        assertEquals(0, AaruyPumpSchedule.shift(winter, TimeZone.getTimeZone("Asia/Shanghai")))
    }

    @Test
    fun aZoneAheadOfThePumpGivesANegativeShift() {
        assertEquals(-2, AaruyPumpSchedule.shift(winter, TimeZone.getTimeZone("Asia/Tokyo")))
    }

    @Test
    fun aHalfHourZoneIsExact() {
        assertEquals(5, AaruyPumpSchedule.shift(winter, TimeZone.getTimeZone("Asia/Kolkata")))
    }

    /** 23:00 in Prague is 06:00 for the pump: the case measured on the real pump. */
    @Test
    fun aRateMovesToTheHourOfThePump() {
        val local = listOf(BasalItem(0, 0.2), BasalItem(46, 0.4))
        assertEquals(listOf(BasalItem(0, 0.2), BasalItem(12, 0.4), BasalItem(14, 0.2)), AaruyPumpSchedule.toPump(local, 14))
    }

    @Test
    fun aFlatProfileStaysOneItem() {
        assertEquals(listOf(BasalItem(0, 0.5)), AaruyPumpSchedule.toPump(listOf(BasalItem(0, 0.5)), 14))
    }

    @Test
    fun theWayBackGivesTheSameProfile() {
        val local = listOf(BasalItem(0, 0.2), BasalItem(12, 0.5), BasalItem(14, 0.3), BasalItem(46, 0.4))
        listOf(14, 12, -2, 5, 0).forEach { shift ->
            assertEquals("shift $shift", local, AaruyPumpSchedule.toLocal(AaruyPumpSchedule.toPump(local, shift), shift))
        }
    }

    @Test
    fun aMovedProfileKeepsItsDailyTotal() {
        val local = listOf(BasalItem(0, 0.2), BasalItem(44, 0.3), BasalItem(46, 0.4))
        assertEquals(5.1, AaruyPumpSchedule.dailyTotal(local), 1e-9)
        assertEquals(5.1, AaruyPumpSchedule.dailyTotal(AaruyPumpSchedule.toPump(local, 14)), 1e-9)
    }
}
