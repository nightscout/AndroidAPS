package app.aaps.pump.aaruy.common.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AaruyUtilsTest {

    private val week = arrayOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

    private fun <T> inLocale(locale: Locale, block: () -> T): T {
        val saved = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return block()
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun roundsHalfUpFromTheDecimalText() {
        assertEquals(1.3, AaruyNumberUtils.getDotOneDouble(1.25), 0.0)
        assertEquals(0.3, AaruyNumberUtils.getDotOneDouble(0.25), 0.0)
        assertEquals(12.0, AaruyNumberUtils.getDotOneDouble(12.0), 0.0)
    }

    @Test
    fun stringsKeepOnlyTheDigitsThatMatter() {
        assertEquals("1.5", AaruyNumberUtils.getDotTwoString(1.5))
        assertEquals("2.0", AaruyNumberUtils.getDotTwoString(2.0))
        assertEquals("1.24", AaruyNumberUtils.getDotTwoString(1.235))
        assertEquals("1.235", AaruyNumberUtils.getDotThreeString(1.2345))
        assertEquals("0.3", AaruyNumberUtils.getDotOneString(0.25))
    }

    @Test
    fun notANumberBecomesZeroText() {
        assertEquals("0.0", AaruyNumberUtils.getDotOneString(Double.NaN))
        assertEquals("0.00", AaruyNumberUtils.getDotTwoString(Double.NaN))
        assertEquals("0.000", AaruyNumberUtils.getDotThreeString(Double.POSITIVE_INFINITY))
        assertEquals(0, AaruyNumberUtils.doubleToInt(Double.NaN))
    }

    @Test
    fun doubleToIntRoundsHalfUp() {
        assertEquals(3, AaruyNumberUtils.doubleToInt(2.5))
        assertEquals(2, AaruyNumberUtils.doubleToInt(2.49))
    }

    @Test
    fun divisionRoundsToTheScale() {
        assertEquals(0.333, AaruyNumberUtils.division(1.0, 3.0, 3), 0.0)
        assertEquals(0.667, AaruyNumberUtils.division(2.0, 3.0, 3), 0.0)
        assertThrows(IllegalArgumentException::class.java) { AaruyNumberUtils.division(1.0, 0.0, 3) }
        assertThrows(IllegalArgumentException::class.java) { AaruyNumberUtils.division(1.0, 3.0, -1) }
    }

    @Test
    fun numbersDoNotDependOnTheLocale() {
        for (tag in listOf("de-DE", "cs-CZ", "ar-SA", "fa-IR", "hi-IN")) {
            inLocale(Locale.forLanguageTag(tag)) {
                assertEquals(tag, 1.3, AaruyNumberUtils.getDotOneDouble(1.25), 0.0)
                assertEquals(tag, "1.5", AaruyNumberUtils.getDotTwoString(1.5))
                assertEquals(tag, "1.235", AaruyNumberUtils.getDotThreeString(1.2345))
            }
        }
    }

    @Test
    fun stampAndDetailsAreInverse() {
        val stamp = AaruyTimeUtil.dateToStamp("2024-03-05 13:14:15")
        assertArrayEquals(intArrayOf(2024, 3, 5, 13, 14, 15), AaruyTimeUtil.getWholeDetail(stamp))
        assertEquals("13:14:15", AaruyTimeUtil.getTime(stamp))
    }

    @Test
    fun weekdaysStartOnSunday() {
        assertEquals("tue", AaruyTimeUtil.getWeekOfDate(AaruyTimeUtil.dateToStamp("2024-03-05 12:00:00"), week))
        assertEquals("sun", AaruyTimeUtil.getWeekOfDate(AaruyTimeUtil.dateToStamp("2024-03-03 00:00:00"), week))
        assertEquals("sat", AaruyTimeUtil.dateToWeek("2024-03-09", week))
    }

    @Test
    fun brokenDateGivesToday() {
        val today = AaruyTimeUtil.getWeekOfDate(System.currentTimeMillis(), week)
        assertEquals(today, AaruyTimeUtil.dateToWeek("not a date", week))
    }

    @Test
    fun minutesBecomeHoursAndMinutes() {
        assertEquals("00:05", AaruyTimeUtil.get_H_m_String(5))
        assertEquals("01:30", AaruyTimeUtil.get_H_m_String(90))
        assertEquals("24:00", AaruyTimeUtil.get_H_m_String(1440))
    }

    @Test
    fun leapYears() {
        assertTrue(AaruyTimeUtil.isLeapYear(2024))
        assertTrue(AaruyTimeUtil.isLeapYear(2000))
        assertFalse(AaruyTimeUtil.isLeapYear(1900))
        assertFalse(AaruyTimeUtil.isLeapYear(2023))
    }
}
