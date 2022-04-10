package info.nightscout.androidaps.interaction.utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import info.nightscout.androidaps.TestBase;
import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.testing.mockers.RawDataMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;
import info.nightscout.shared.sharedPreferences.SP;

/**
 * This test covers DisplayFormat class (directly)
 * but also SmallestDoubleString - due to carefully chosen input data to format
 */

public class DisplayFormatTest extends TestBase {

    private DisplayFormat displayFormat;
    private RawDataMocker rawDataMocker;

    private WearUtil wearUtil;
    private WearUtilMocker wearUtilMocker;
    @Mock SP sp;

    @Before
    public void mock() {
        wearUtil = spy(new WearUtil());
        rawDataMocker = new RawDataMocker(wearUtil);
        wearUtilMocker = new WearUtilMocker(wearUtil);
        wearUtilMocker.prepareMock();

        displayFormat = new DisplayFormat();
        displayFormat.wearUtil = wearUtil;
        displayFormat.sp = sp;

        when(sp.getBoolean("complication_unicode", true)).thenReturn(true);
    }

    @Test
    public void shortTimeSinceTest() {

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 0, 0)), "0'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 0, 5)), "0'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 0, 55)), "0'");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 1, 0)), "1'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 1, 59)), "1'");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 2, 0)), "2'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 3, 0)), "3'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 4, 0)), "4'");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 10, 0)), "10'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 30, 0)), "30'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 59, 0)), "59'");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 0, 59, 59)), "59'");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 1, 0, 0)), "1h");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 1, 30, 0)), "1h");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 1, 59, 59)), "1h");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 2, 0, 0)), "2h");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 3, 0, 0)), "3h");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 4, 0, 0)), "4h");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 5, 0, 0)), "5h");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 12, 0, 0)), "12h");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 18, 0, 0)), "18h");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(0, 23, 59, 59)), "23h");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(1, 0, 0, 0)), "1d");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(1, 12, 0, 0)), "1d");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(1, 23, 59, 59)), "1d");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(2, 0, 0, 0)), "2d");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(3, 0, 0, 0)), "3d");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(4, 0, 0, 0)), "4d");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(5, 0, 0, 0)), "5d");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(6, 0, 0, 0)), "6d");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(6, 23, 59, 59)), "6d");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(7, 0, 0, 0)), "1w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(8, 0, 0, 0)), "1w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(9, 0, 0, 0)), "1w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(13, 23, 59, 59)), "1w");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(14, 0, 0, 0)), "2w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(21, 0, 0, 0)), "3w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(28, 0, 0, 0)), "4w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(31, 0, 0, 0)), "4w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(32, 0, 0, 0)), "4w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(35, 0, 0, 0)), "5w");

        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(100, 0, 0, 0)), "14w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(200, 0, 0, 0)), "28w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(365, 0, 0, 0)), "52w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(366, 0, 0, 0)), "52w");
        assertEquals(displayFormat.shortTimeSince(wearUtilMocker.backInTime(367, 0, 0, 0)), "52w");
    }

    @Test
    public void shortTrendTest() {
        RawDisplayData raw = new RawDisplayData(wearUtil);
        assertEquals(displayFormat.shortTrend(raw), "-- Δ--");

        raw.datetime = wearUtilMocker.backInTime(0, 0, 2, 0);
        assertEquals(displayFormat.shortTrend(raw), "2' Δ--");

        when(sp.getBoolean("complication_unicode", true)).thenReturn(true);

        // shortening
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1.2")), "2' Δ1.2");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(11, "1.2")), "11' 1.2");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(12, "0.7")), "12' Δ.7");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "1.0")), "10' Δ1");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(14, "-5.0")), "14' Δ-5");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(13, "-5.1")), "13' -5");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(15, "0.87")), "15' .87");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "-1.78")), "10' -2");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(3, "2.549")), "3' 2.55");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(1, "-1.563")), "1' -1.6");

        // preserving separator
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1,2")), "2' Δ1,2");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(15, "0,87")), "15' ,87");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(3, "+2,549")), "3' 2,55");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(1, "-1,563")), "1' -1,6");

        // UTF-off mode - without delta symbol
        when(sp.getBoolean("complication_unicode", true)).thenReturn(false);
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(2, "1.2")), "2' 1.2");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(12, "0.7")), "12' 0.7");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(10, "1.0")), "10' 1.0");
        assertEquals(displayFormat.shortTrend(rawDataMocker.rawDelta(14, "-5.0")), "14' -5");
    }

    @Test
    public void longGlucoseLine() {
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("125", 2, "1.2")), "125→ Δ1.2 (2')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("97", 11, "5.2")), "97↗ Δ5.2 (11')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("110", 12, "0.7")), "110→ Δ.7 (12')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("65", 10, "7.0")), "65↗ Δ7 (10')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("215", 14, "-5.0")), "215↘ Δ-5 (14')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("8.3", 13, "-5.1")), "8.3↘ Δ-5.1 (13')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("6.8", 15, "10.83")), "6.8↑ Δ10.83 (15')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("13.2", 10, "-11.78")), "13.2↓ Δ-11.78 (10')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("3.9", 3, "2.549")), "3.9→ Δ2.549 (3')");
        assertEquals(displayFormat.longGlucoseLine(rawDataMocker.rawSgv("11.1", 1, "-15.563")), "11.1↓ Δ-15.563 (1')");
    }

    @Test
    public void longDetailsLineTest() {
        when(sp.getBoolean("complication_unicode", true)).thenReturn(true);
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0g", "0U", "3.5U/h")), "0g  ⁞  0U  ⁞  ⎍ 3.5U/h");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("50g", "7.56U", "0%")), "50g  ⁞  7.56U  ⁞  ⎍ 0%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("12g", "3.23U", "120%")), "12g ⁞ 3.23U ⁞ 120%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("2(40)g", "-1.5U", "0.55U/h")), "2(40)g ⁞ -2U ⁞ 0.55U/h");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0(24)g", "0.05U", "160%")), "0(24)g ⁞ 0.05U ⁞ 160%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("47g", "13.87U", "220%")), "47g ⁞ 13.87U ⁞ 220%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("13(5)g", "5.90U", "300%")), "13(5)g ⁞ 5.90U ⁞ 300%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("11(50)g", "0U", "70%")), "11(50)g ⁞ 0U ⁞ 70%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("7g", "0.54U", "30%")), "7g  ⁞  0.54U  ⁞  ⎍ 30%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("19(38)g", "35.545U", "12.9U/h")), "19g ⁞ 36U ⁞ 12.9U/h");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("100(1)g", "12.345U", "6.98647U/h")), "100g 12U 6.98647U/h");

        when(sp.getBoolean("complication_unicode", true)).thenReturn(false);
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("0g", "0U", "3.5U/h")), "0g  |  0U  |  3.5U/h");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("50g", "7.56U", "0%")), "50g  |  7.56U  |  0%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("12g", "3.23U", "120%")), "12g  |  3.23U  |  120%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("7g", "0.54U", "30%")), "7g  |  0.54U  |  30%");
        assertEquals(displayFormat.longDetailsLine(rawDataMocker.rawCobIobBr("19(38)g", "35.545U", "12.9U/h")), "19g | 36U | 12.9U/h");
    }

    @Test
    public void detailedIobTest() {
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-1.29U", "(0,910|-2,20)")), Pair.create("-1.29U", ",91 -2"));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("3.50U", "")), Pair.create("3.50U", ""));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("12.5U", "(+1,4|-4.78)")), Pair.create("12.5U", "1,4 -5"));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("0.67U", "some junks")), Pair.create(".67U", ""));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-11.0U", "(broken|data)")), Pair.create("-11U", "-- --"));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("5.52U", "(0,5439|wrong)")), Pair.create("5.52U", ",54 --"));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-8.1U", "(|-8,1)")), Pair.create("-8.1U", "-- -8"));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-8.1U", "(|-8,1)")), Pair.create("-8.1U", "-- -8"));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("7.6U", "(malformed)")), Pair.create("7.6U", ""));
        assertEquals(displayFormat.detailedIob(rawDataMocker.rawIob("-4.26U", "(6,97|1,3422|too much)")), Pair.create("-4.26U", "7 1,3"));
    }

    @Test
    public void detailedCobTest() {
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("0g")), Pair.create("0g", ""));
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("50g")), Pair.create("50g", ""));
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("2(40)g")), Pair.create("2g", "40g"));
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("0(24)g")), Pair.create("0g", "24g"));
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("13(5)g")), Pair.create("13g", "5g"));
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("11(50)g")), Pair.create("11g", "50g"));
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("19(38)g")), Pair.create("19g", "38g"));
        assertEquals(displayFormat.detailedCob(rawDataMocker.rawCob("100(1)g")), Pair.create("100g", "1g"));
    }

}
