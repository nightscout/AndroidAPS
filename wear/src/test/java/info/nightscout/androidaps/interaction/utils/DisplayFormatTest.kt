package info.nightscout.androidaps.interaction.utils

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.R
import info.nightscout.androidaps.WearTestBase
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.androidaps.testing.mockers.RawDataMocker
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
        rawDataMocker = RawDataMocker()
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
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 0, 0))).isEqualTo("0'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 0, 5))).isEqualTo("0'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 0, 55))).isEqualTo("0'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 1, 0))).isEqualTo("1'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 1, 59))).isEqualTo("1'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 2, 0))).isEqualTo("2'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 3, 0))).isEqualTo("3'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 4, 0))).isEqualTo("4'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 10, 0))).isEqualTo("10'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 30, 0))).isEqualTo("30'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 59, 0))).isEqualTo("59'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 0, 59, 59))).isEqualTo("59'")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 1, 0, 0))).isEqualTo("1h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 1, 30, 0))).isEqualTo("1h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 1, 59, 59))).isEqualTo("1h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 2, 0, 0))).isEqualTo("2h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 3, 0, 0))).isEqualTo("3h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 4, 0, 0))).isEqualTo("4h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 5, 0, 0))).isEqualTo("5h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 12, 0, 0))).isEqualTo("12h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 18, 0, 0))).isEqualTo("18h")
        assertThat(displayFormat.shortTimeSince(backInTime(0, 23, 59, 59))).isEqualTo("23h")
        assertThat(displayFormat.shortTimeSince(backInTime(1, 0, 0, 0))).isEqualTo("1d")
        assertThat(displayFormat.shortTimeSince(backInTime(1, 12, 0, 0))).isEqualTo("1d")
        assertThat(displayFormat.shortTimeSince(backInTime(1, 23, 59, 59))).isEqualTo("1d")
        assertThat(displayFormat.shortTimeSince(backInTime(2, 0, 0, 0))).isEqualTo("2d")
        assertThat(displayFormat.shortTimeSince(backInTime(3, 0, 0, 0))).isEqualTo("3d")
        assertThat(displayFormat.shortTimeSince(backInTime(4, 0, 0, 0))).isEqualTo("4d")
        assertThat(displayFormat.shortTimeSince(backInTime(5, 0, 0, 0))).isEqualTo("5d")
        assertThat(displayFormat.shortTimeSince(backInTime(6, 0, 0, 0))).isEqualTo("6d")
        assertThat(displayFormat.shortTimeSince(backInTime(6, 23, 59, 59))).isEqualTo("6d")
        assertThat(displayFormat.shortTimeSince(backInTime(7, 0, 0, 0))).isEqualTo("1w")
        assertThat(displayFormat.shortTimeSince(backInTime(8, 0, 0, 0))).isEqualTo("1w")
        assertThat(displayFormat.shortTimeSince(backInTime(9, 0, 0, 0))).isEqualTo("1w")
        assertThat(displayFormat.shortTimeSince(backInTime(13, 23, 59, 59))).isEqualTo("1w")
        assertThat(displayFormat.shortTimeSince(backInTime(14, 0, 0, 0))).isEqualTo("2w")
        assertThat(displayFormat.shortTimeSince(backInTime(21, 0, 0, 0))).isEqualTo("3w")
        assertThat(displayFormat.shortTimeSince(backInTime(28, 0, 0, 0))).isEqualTo("4w")
        assertThat(displayFormat.shortTimeSince(backInTime(31, 0, 0, 0))).isEqualTo("4w")
        assertThat(displayFormat.shortTimeSince(backInTime(32, 0, 0, 0))).isEqualTo("4w")
        assertThat(displayFormat.shortTimeSince(backInTime(35, 0, 0, 0))).isEqualTo("5w")
        assertThat(displayFormat.shortTimeSince(backInTime(100, 0, 0, 0))).isEqualTo("14w")
        assertThat(displayFormat.shortTimeSince(backInTime(200, 0, 0, 0))).isEqualTo("28w")
        assertThat(displayFormat.shortTimeSince(backInTime(365, 0, 0, 0))).isEqualTo("52w")
        assertThat(displayFormat.shortTimeSince(backInTime(366, 0, 0, 0))).isEqualTo("52w")
        assertThat(displayFormat.shortTimeSince(backInTime(367, 0, 0, 0))).isEqualTo("52w")
    }

    @Test fun shortTrendTest() {
        val raw = RawDisplayData()
        assertThat(displayFormat.shortTrend(raw)).isEqualTo("-- Δ--")
        raw.singleBg.timeStamp = backInTime(0, 0, 2, 0)
        assertThat(displayFormat.shortTrend(raw)).isEqualTo("2' Δ--")
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(true)

        // shortening
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1.2"))).isEqualTo("2' Δ1.2")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(11, "1.2"))).isEqualTo("11' 1.2")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(12, "0.7"))).isEqualTo("12' Δ.7")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "1.0"))).isEqualTo("10' Δ1")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(14, "-5.0"))).isEqualTo("14' Δ-5")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(13, "-5.1"))).isEqualTo("13' -5")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(15, "0.87"))).isEqualTo("15' .87")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "-1.78"))).isEqualTo("10' -2")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(3, "2.549"))).isEqualTo("3' 2.55")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(1, "-1.563"))).isEqualTo("1' -1.6")

        // preserving separator
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1,2"))).isEqualTo("2' Δ1,2")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(15, "0,87"))).isEqualTo("15' ,87")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(3, "+2,549"))).isEqualTo("3' 2,55")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(1, "-1,563"))).isEqualTo("1' -1,6")

        // UTF-off mode - without delta symbol
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(false)
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1.2"))).isEqualTo("2' 1.2")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(12, "0.7"))).isEqualTo("12' 0.7")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "1.0"))).isEqualTo("10' 1.0")
        assertThat(displayFormat.shortTrend(rawDataMocker.rawDelta(14, "-5.0"))).isEqualTo("14' -5")
    }

    @Test fun longGlucoseLine() {
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("125", 2, "1.2"))).isEqualTo("125→ Δ1.2 (2')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("97", 11, "5.2"))).isEqualTo("97↗ Δ5.2 (11')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("110", 12, "0.7"))).isEqualTo("110→ Δ.7 (12')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("65", 10, "7.0"))).isEqualTo("65↗ Δ7 (10')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("215", 14, "-5.0"))).isEqualTo("215↘ Δ-5 (14')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("8.3", 13, "-5.1"))).isEqualTo("8.3↘ Δ-5.1 (13')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("6.8", 15, "10.83"))).isEqualTo("6.8↑ Δ10.83 (15')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("13.2", 10, "-11.78"))).isEqualTo("13.2↓ Δ-11.78 (10')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("3.9", 3, "2.549"))).isEqualTo("3.9→ Δ2.549 (3')")
        assertThat(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("11.1", 1, "-15.563"))).isEqualTo("11.1↓ Δ-15.563 (1')")
    }

    @Test fun longDetailsLineTest() {
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(true)
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0g", "0U", "3.5U/h"))).isEqualTo("0g  ⁞  0U  ⁞  ⎍ 3.5U/h")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("50g", "7.56U", "0%"))).isEqualTo("50g  ⁞  7.56U  ⁞  ⎍ 0%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("12g", "3.23U", "120%"))).isEqualTo("12g ⁞ 3.23U ⁞ 120%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("2(40)g", "-1.5U", "0.55U/h"))).isEqualTo("2(40)g ⁞ -2U ⁞ 0.55U/h")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0(24)g", "0.05U", "160%"))).isEqualTo("0(24)g ⁞ 0.05U ⁞ 160%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("47g", "13.87U", "220%"))).isEqualTo("47g ⁞ 13.87U ⁞ 220%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("13(5)g", "5.90U", "300%"))).isEqualTo("13(5)g ⁞ 5.90U ⁞ 300%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("11(50)g", "0U", "70%"))).isEqualTo("11(50)g ⁞ 0U ⁞ 70%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("7g", "0.54U", "30%"))).isEqualTo("7g  ⁞  0.54U  ⁞  ⎍ 30%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("19(38)g", "35.545U", "12.9U/h"))).isEqualTo("19g ⁞ 36U ⁞ 12.9U/h")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("100(1)g", "12.345U", "6.98647U/h"))).isEqualTo("100g 12U 6.98647U/h")
        Mockito.`when`(sp.getBoolean("complication_unicode", true)).thenReturn(false)
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0g", "0U", "3.5U/h"))).isEqualTo("0g  |  0U  |  3.5U/h")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("50g", "7.56U", "0%"))).isEqualTo("50g  |  7.56U  |  0%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("12g", "3.23U", "120%"))).isEqualTo("12g  |  3.23U  |  120%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("7g", "0.54U", "30%"))).isEqualTo("7g  |  0.54U  |  30%")
        assertThat(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("19(38)g", "35.545U", "12.9U/h"))).isEqualTo("19g | 36U | 12.9U/h")
    }

    @Test fun detailedIobTest() {
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("-1.29U", "(0,910|-2,20)"))).isEqualTo(Pair.create("-1.29U", ",91 -2"))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("3.50U", ""))).isEqualTo(Pair.create("3.50U", ""))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("12.5U", "(+1,4|-4.78)"))).isEqualTo(Pair.create("12.5U", "1,4 -5"))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("0.67U", "some junks"))).isEqualTo(Pair.create(".67U", ""))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("-11.0U", "(broken|data)"))).isEqualTo(Pair.create("-11U", "-- --"))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("5.52U", "(0,5439|wrong)"))).isEqualTo(Pair.create("5.52U", ",54 --"))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("-8.1U", "(|-8,1)"))).isEqualTo(Pair.create("-8.1U", "-- -8"))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("-8.1U", "(|-8,1)"))).isEqualTo(Pair.create("-8.1U", "-- -8"))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("7.6U", "(malformed)"))).isEqualTo(Pair.create("7.6U", ""))
        assertThat(displayFormat.detailedIob(rawDataMocker.rawIob("-4.26U", "(6,97|1,3422|too much)"))).isEqualTo(Pair.create("-4.26U", "7 1,3"))
    }

    @Test fun detailedCobTest() {
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("0g"))).isEqualTo(Pair.create("0g", ""))
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("50g"))).isEqualTo(Pair.create("50g", ""))
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("2(40)g"))).isEqualTo(Pair.create("2g", "40g"))
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("0(24)g"))).isEqualTo(Pair.create("0g", "24g"))
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("13(5)g"))).isEqualTo(Pair.create("13g", "5g"))
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("11(50)g"))).isEqualTo(Pair.create("11g", "50g"))
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("19(38)g"))).isEqualTo(Pair.create("19g", "38g"))
        assertThat(displayFormat.detailedCob(rawDataMocker.rawCob("100(1)g"))).isEqualTo(Pair.create("100g", "1g"))
    }
}
