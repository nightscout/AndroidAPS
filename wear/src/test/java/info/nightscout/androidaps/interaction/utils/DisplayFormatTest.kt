package info.nightscout.androidaps.interaction.utils

import info.nightscout.androidaps.R
import info.nightscout.androidaps.WearTestBase
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.androidaps.testing.mockers.RawDataMocker
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * This test covers DisplayFormat class (directly)
 * but also SmallestDoubleString - due to carefully chosen input data to format
 */
class DisplayFormatTest : WearTestBase() {

    private lateinit var displayFormat: DisplayFormat
    private lateinit var rawDataMocker: RawDataMocker

    @BeforeEach
    fun mock() {
        rawDataMocker = RawDataMocker(wearUtil)
        wearUtilMocker.prepareMock()
        displayFormat = DisplayFormat()
        displayFormat.wearUtil = wearUtil
        displayFormat.sp = sp
        displayFormat.context = context
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(true)
        Mockito.`when`(context.getString(R.string.hour_short)).thenReturn("h")
        Mockito.`when`(context.getString(R.string.day_short)).thenReturn("d")
        Mockito.`when`(context.getString(R.string.week_short)).thenReturn("w")
    }

    @Test fun shortTimeSinceTest() {
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 0, 0)), "0'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 0, 5)), "0'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 0, 55)), "0'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 1, 0)), "1'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 1, 59)), "1'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 2, 0)), "2'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 3, 0)), "3'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 4, 0)), "4'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 10, 0)), "10'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 30, 0)), "30'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 59, 0)), "59'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 59, 59)), "59'")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 1, 0, 0)), "1h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 1, 30, 0)), "1h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 1, 59, 59)), "1h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 2, 0, 0)), "2h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 3, 0, 0)), "3h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 4, 0, 0)), "4h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 5, 0, 0)), "5h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 12, 0, 0)), "12h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 18, 0, 0)), "18h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 23, 59, 59)), "23h")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(1, 0, 0, 0)), "1d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(1, 12, 0, 0)), "1d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(1, 23, 59, 59)), "1d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(2, 0, 0, 0)), "2d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(3, 0, 0, 0)), "3d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(4, 0, 0, 0)), "4d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(5, 0, 0, 0)), "5d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(6, 0, 0, 0)), "6d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(6, 23, 59, 59)), "6d")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(7, 0, 0, 0)), "1w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(8, 0, 0, 0)), "1w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(9, 0, 0, 0)), "1w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(13, 23, 59, 59)), "1w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(14, 0, 0, 0)), "2w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(21, 0, 0, 0)), "3w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(28, 0, 0, 0)), "4w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(31, 0, 0, 0)), "4w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(32, 0, 0, 0)), "4w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(35, 0, 0, 0)), "5w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(100, 0, 0, 0)), "14w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(200, 0, 0, 0)), "28w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(365, 0, 0, 0)), "52w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(366, 0, 0, 0)), "52w")
        Assertions.assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(367, 0, 0, 0)), "52w")
    }

    @Test fun shortTrendTest() {
        val raw = RawDisplayData()
        Assertions.assertEquals(displayFormat.shortTrend(raw), "-- Δ--")
        raw.singleBg.timeStamp = wearUtilMocker.backInTime(0, 0, 2, 0)
        Assertions.assertEquals(displayFormat.shortTrend(raw), "2' Δ--")
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(true)

        // shortening
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1.2")), "2' Δ1.2")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(11, "1.2")), "11' 1.2")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(12, "0.7")), "12' Δ.7")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "1.0")), "10' Δ1")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(14, "-5.0")), "14' Δ-5")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(13, "-5.1")), "13' -5")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(15, "0.87")), "15' .87")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "-1.78")), "10' -2")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(3, "2.549")), "3' 2.55")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(1, "-1.563")), "1' -1.6")

        // preserving separator
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1,2")), "2' Δ1,2")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(15, "0,87")), "15' ,87")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(3, "+2,549")), "3' 2,55")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(1, "-1,563")), "1' -1,6")

        // UTF-off mode - without delta symbol
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(false)
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1.2")), "2' 1.2")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(12, "0.7")), "12' 0.7")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "1.0")), "10' 1.0")
        Assertions.assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(14, "-5.0")), "14' -5")
    }

    @Test fun longGlucoseLine() {
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("125", 2, "1.2")), "125→ Δ1.2 (2')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("97", 11, "5.2")), "97↗ Δ5.2 (11')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("110", 12, "0.7")), "110→ Δ.7 (12')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("65", 10, "7.0")), "65↗ Δ7 (10')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("215", 14, "-5.0")), "215↘ Δ-5 (14')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("8.3", 13, "-5.1")), "8.3↘ Δ-5.1 (13')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("6.8", 15, "10.83")), "6.8↑ Δ10.83 (15')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("13.2", 10, "-11.78")), "13.2↓ Δ-11.78 (10')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("3.9", 3, "2.549")), "3.9→ Δ2.549 (3')")
        Assertions.assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("11.1", 1, "-15.563")), "11.1↓ Δ-15.563 (1')")
    }

    @Test fun longDetailsLineTest() {
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(true)
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0g", "0U", "3.5U/h")), "0g  ⁞  0U  ⁞  ⎍ 3.5U/h")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("50g", "7.56U", "0%")), "50g  ⁞  7.56U  ⁞  ⎍ 0%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("12g", "3.23U", "120%")), "12g ⁞ 3.23U ⁞ 120%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("2(40)g", "-1.5U", "0.55U/h")), "2(40)g ⁞ -2U ⁞ 0.55U/h")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0(24)g", "0.05U", "160%")), "0(24)g ⁞ 0.05U ⁞ 160%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("47g", "13.87U", "220%")), "47g ⁞ 13.87U ⁞ 220%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("13(5)g", "5.90U", "300%")), "13(5)g ⁞ 5.90U ⁞ 300%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("11(50)g", "0U", "70%")), "11(50)g ⁞ 0U ⁞ 70%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("7g", "0.54U", "30%")), "7g  ⁞  0.54U  ⁞  ⎍ 30%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("19(38)g", "35.545U", "12.9U/h")), "19g ⁞ 36U ⁞ 12.9U/h")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("100(1)g", "12.345U", "6.98647U/h")), "100g 12U 6.98647U/h")
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(false)
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0g", "0U", "3.5U/h")), "0g  |  0U  |  3.5U/h")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("50g", "7.56U", "0%")), "50g  |  7.56U  |  0%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("12g", "3.23U", "120%")), "12g  |  3.23U  |  120%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("7g", "0.54U", "30%")), "7g  |  0.54U  |  30%")
        Assertions.assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("19(38)g", "35.545U", "12.9U/h")), "19g | 36U | 12.9U/h")
    }

    @Test fun detailedIobTest() {
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-1.29U", "(0,910|-2,20)")), Pair.create("-1.29U", ",91 -2"))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("3.50U", "")), Pair.create("3.50U", ""))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("12.5U", "(+1,4|-4.78)")), Pair.create("12.5U", "1,4 -5"))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("0.67U", "some junks")), Pair.create(".67U", ""))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-11.0U", "(broken|data)")), Pair.create("-11U", "-- --"))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("5.52U", "(0,5439|wrong)")), Pair.create("5.52U", ",54 --"))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-8.1U", "(|-8,1)")), Pair.create("-8.1U", "-- -8"))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-8.1U", "(|-8,1)")), Pair.create("-8.1U", "-- -8"))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("7.6U", "(malformed)")), Pair.create("7.6U", ""))
        Assertions.assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-4.26U", "(6,97|1,3422|too much)")), Pair.create("-4.26U", "7 1,3"))
    }

    @Test fun detailedCobTest() {
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("0g")), Pair.create("0g", ""))
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("50g")), Pair.create("50g", ""))
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("2(40)g")), Pair.create("2g", "40g"))
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("0(24)g")), Pair.create("0g", "24g"))
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("13(5)g")), Pair.create("13g", "5g"))
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("11(50)g")), Pair.create("11g", "50g"))
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("19(38)g")), Pair.create("19g", "38g"))
        Assertions.assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("100(1)g")), Pair.create("100g", "1g"))
    }
}