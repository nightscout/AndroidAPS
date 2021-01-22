package info.nightscout.androidaps.interaction.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.Aaps;
import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.testing.mockers.AAPSMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;

import static info.nightscout.androidaps.testing.mockers.RawDataMocker.rawCob;
import static info.nightscout.androidaps.testing.mockers.RawDataMocker.rawCobIobBr;
import static info.nightscout.androidaps.testing.mockers.RawDataMocker.rawDelta;
import static info.nightscout.androidaps.testing.mockers.RawDataMocker.rawIob;
import static info.nightscout.androidaps.testing.mockers.RawDataMocker.rawSgv;
import static info.nightscout.androidaps.testing.mockers.WearUtilMocker.backInTime;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * This test covers DisplayFormat class (directly)
 * but also SmallestDoubleString - due to carefully chosen input data to format
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class, Log.class, SharedPreferences.class, Context.class, Aaps.class } )
public class DisplayFormatTest {

    @Before
    public void mock() throws Exception {
        WearUtilMocker.prepareMock();
        AAPSMocker.prepareMock();
        AAPSMocker.resetMockedSharedPrefs();
    }

    @Test
    public void shortTimeSinceTest() {

        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,0,0)), is("0'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,0,5)), is("0'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,0,55)), is("0'"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,1,0)), is("1'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,1,59)), is("1'"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,2,0)), is("2'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,3,0)), is("3'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,4,0)), is("4'"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,10,0)), is("10'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,30,0)), is("30'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,59,0)), is("59'"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,0,59,59)), is("59'"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(0,1,0,0)), is("1h"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,1,30,0)), is("1h"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,1,59,59)), is("1h"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(0,2,0,0)), is("2h"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,3,0,0)), is("3h"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,4,0,0)), is("4h"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,5,0,0)), is("5h"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(0,12,0,0)), is("12h"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,18,0,0)), is("18h"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(0,23,59,59)), is("23h"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(1,0,0,0)), is("1d"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(1,12,0,0)), is("1d"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(1,23,59,59)), is("1d"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(2,0,0,0)), is("2d"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(3,0,0,0)), is("3d"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(4,0,0,0)), is("4d"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(5,0,0,0)), is("5d"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(6,0,0,0)), is("6d"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(6,23,59,59)), is("6d"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(7,0,0,0)), is("1w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(8,0,0,0)), is("1w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(9,0,0,0)), is("1w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(13,23,59,59)), is("1w"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(14,0,0,0)), is("2w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(21,0,0,0)), is("3w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(28,0,0,0)), is("4w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(31,0,0,0)), is("4w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(32,0,0,0)), is("4w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(35,0,0,0)), is("5w"));

        assertThat(DisplayFormat.shortTimeSince(backInTime(100,0,0,0)), is("14w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(200,0,0,0)), is("28w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(365,0,0,0)), is("52w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(366,0,0,0)), is("52w"));
        assertThat(DisplayFormat.shortTimeSince(backInTime(367,0,0,0)), is("52w"));
    }

    @Test
    public void shortTrendTest() {
        RawDisplayData raw = new RawDisplayData();
        assertThat(DisplayFormat.shortTrend(raw), is("-- Δ--"));

        raw.datetime = backInTime(0, 0, 2, 0);
        assertThat(DisplayFormat.shortTrend(raw), is("2' Δ--"));

        AAPSMocker.setMockedUnicodeComplicationsOn(true);

        // shortening
        assertThat(DisplayFormat.shortTrend(rawDelta(2, "1.2")), is("2' Δ1.2"));
        assertThat(DisplayFormat.shortTrend(rawDelta(11,"1.2")), is("11' 1.2"));
        assertThat(DisplayFormat.shortTrend(rawDelta(12,"0.7")), is("12' Δ.7"));
        assertThat(DisplayFormat.shortTrend(rawDelta(10,"1.0")), is("10' Δ1"));
        assertThat(DisplayFormat.shortTrend(rawDelta(14,"-5.0")), is("14' Δ-5"));
        assertThat(DisplayFormat.shortTrend(rawDelta(13,"-5.1")), is("13' -5"));
        assertThat(DisplayFormat.shortTrend(rawDelta(15,"0.87")), is("15' .87"));
        assertThat(DisplayFormat.shortTrend(rawDelta(10,"-1.78")), is("10' -2"));
        assertThat(DisplayFormat.shortTrend(rawDelta(3, "2.549")), is("3' 2.55"));
        assertThat(DisplayFormat.shortTrend(rawDelta(1, "-1.563")), is("1' -1.6"));

        // preserving separator
        assertThat(DisplayFormat.shortTrend(rawDelta(2, "1,2")), is("2' Δ1,2"));
        assertThat(DisplayFormat.shortTrend(rawDelta(15,"0,87")), is("15' ,87"));
        assertThat(DisplayFormat.shortTrend(rawDelta(3, "+2,549")), is("3' 2,55"));
        assertThat(DisplayFormat.shortTrend(rawDelta(1, "-1,563")), is("1' -1,6"));

        // UTF-off mode - without delta symbol
        AAPSMocker.setMockedUnicodeComplicationsOn(false);
        assertThat(DisplayFormat.shortTrend(rawDelta(2, "1.2")), is("2' 1.2"));
        assertThat(DisplayFormat.shortTrend(rawDelta(12,"0.7")), is("12' 0.7"));
        assertThat(DisplayFormat.shortTrend(rawDelta(10,"1.0")), is("10' 1.0"));
        assertThat(DisplayFormat.shortTrend(rawDelta(14,"-5.0")), is("14' -5"));
    }

    @Test
    public void longGlucoseLine() {
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("125",2, "1.2")), is("125→ Δ1.2 (2')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("97",11, "5.2")), is("97↗ Δ5.2 (11')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("110",12,"0.7")), is("110→ Δ.7 (12')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("65",10,"7.0")), is("65↗ Δ7 (10')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("215",14,"-5.0")), is("215↘ Δ-5 (14')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("8.3",13,"-5.1")), is("8.3↘ Δ-5.1 (13')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("6.8",15,"10.83")), is("6.8↑ Δ10.83 (15')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("13.2",10,"-11.78")), is("13.2↓ Δ-11.78 (10')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("3.9",3, "2.549")), is("3.9→ Δ2.549 (3')"));
        assertThat(DisplayFormat.longGlucoseLine(rawSgv("11.1",1, "-15.563")), is("11.1↓ Δ-15.563 (1')"));
    }

    @Test
    public void longDetailsLineTest() {
        AAPSMocker.setMockedUnicodeComplicationsOn(true);
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("0g", "0U", "3.5U/h")), is("0g  ⁞  0U  ⁞  ⎍ 3.5U/h"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("50g", "7.56U", "0%")), is("50g  ⁞  7.56U  ⁞  ⎍ 0%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("12g", "3.23U", "120%")), is("12g ⁞ 3.23U ⁞ 120%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("2(40)g", "-1.5U", "0.55U/h")), is("2(40)g ⁞ -2U ⁞ 0.55U/h"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("0(24)g", "0.05U", "160%")), is("0(24)g ⁞ 0.05U ⁞ 160%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("47g", "13.87U", "220%")), is("47g ⁞ 13.87U ⁞ 220%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("13(5)g", "5.90U", "300%")), is("13(5)g ⁞ 5.90U ⁞ 300%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("11(50)g", "0U", "70%")), is("11(50)g ⁞ 0U ⁞ 70%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("7g", "0.54U", "30%")), is("7g  ⁞  0.54U  ⁞  ⎍ 30%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("19(38)g", "35.545U", "12.9U/h")), is("19g ⁞ 36U ⁞ 12.9U/h"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("100(1)g", "12.345U", "6.98647U/h")), is("100g 12U 6.98647U/h"));

        AAPSMocker.setMockedUnicodeComplicationsOn(false);
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("0g", "0U", "3.5U/h")), is("0g  |  0U  |  3.5U/h"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("50g", "7.56U", "0%")), is("50g  |  7.56U  |  0%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("12g", "3.23U", "120%")), is("12g  |  3.23U  |  120%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("7g", "0.54U", "30%")), is("7g  |  0.54U  |  30%"));
        assertThat(DisplayFormat.longDetailsLine(rawCobIobBr("19(38)g", "35.545U", "12.9U/h")), is("19g | 36U | 12.9U/h"));
    }

    @Test
    public void detailedIobTest() {
        assertThat(DisplayFormat.detailedIob(rawIob("-1.29U", "(0,910|-2,20)")), is(Pair.create("-1.29U", ",91 -2")));
        assertThat(DisplayFormat.detailedIob(rawIob("3.50U", "")), is(Pair.create("3.50U", "")));
        assertThat(DisplayFormat.detailedIob(rawIob("12.5U", "(+1,4|-4.78)")), is(Pair.create("12.5U", "1,4 -5")));
        assertThat(DisplayFormat.detailedIob(rawIob("0.67U", "some junks")), is(Pair.create(".67U", "")));
        assertThat(DisplayFormat.detailedIob(rawIob("-11.0U", "(broken|data)")), is(Pair.create("-11U", "-- --")));
        assertThat(DisplayFormat.detailedIob(rawIob("5.52U", "(0,5439|wrong)")), is(Pair.create("5.52U", ",54 --")));
        assertThat(DisplayFormat.detailedIob(rawIob("-8.1U", "(|-8,1)")), is(Pair.create("-8.1U", "-- -8")));
        assertThat(DisplayFormat.detailedIob(rawIob("-8.1U", "(|-8,1)")), is(Pair.create("-8.1U", "-- -8")));
        assertThat(DisplayFormat.detailedIob(rawIob("7.6U", "(malformed)")), is(Pair.create("7.6U", "")));
        assertThat(DisplayFormat.detailedIob(rawIob("-4.26U", "(6,97|1,3422|too much)")), is(Pair.create("-4.26U", "7 1,3")));
    }

    @Test
    public void detailedCobTest() {
        assertThat(DisplayFormat.detailedCob(rawCob("0g")), is(Pair.create("0g", "")));
        assertThat(DisplayFormat.detailedCob(rawCob("50g")), is(Pair.create("50g", "")));
        assertThat(DisplayFormat.detailedCob(rawCob("2(40)g")), is(Pair.create("2g", "40g")));
        assertThat(DisplayFormat.detailedCob(rawCob("0(24)g")), is(Pair.create("0g", "24g")));
        assertThat(DisplayFormat.detailedCob(rawCob("13(5)g")), is(Pair.create("13g", "5g")));
        assertThat(DisplayFormat.detailedCob(rawCob("11(50)g")), is(Pair.create("11g", "50g")));
        assertThat(DisplayFormat.detailedCob(rawCob("19(38)g")), is(Pair.create("19g", "38g")));
        assertThat(DisplayFormat.detailedCob(rawCob("100(1)g")), is(Pair.create("100g", "1g")));
    }

}
