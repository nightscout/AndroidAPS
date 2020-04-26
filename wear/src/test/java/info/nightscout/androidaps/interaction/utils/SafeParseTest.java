package info.nightscout.androidaps.interaction.utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by dlvoy on 21.11.2019.
 */
@RunWith(PowerMockRunner.class)
public class SafeParseTest {

    @Test
    public void stringToDoubleTest() {
        // correct values
        assertThat(0.1234, is(SafeParse.stringToDouble("0.1234")));
        assertThat(0.1234, is(SafeParse.stringToDouble("0,1234")));
        assertThat(0.5436564812, is(SafeParse.stringToDouble(".5436564812")));
        assertThat(0.5436564812, is(SafeParse.stringToDouble(",5436564812")));
        assertThat(1000500100900.0, is(SafeParse.stringToDouble("1000500100900")));
        assertThat(42.0, is(SafeParse.stringToDouble("42")));

        // units or other extra values are not permitted
        assertThat(0.0, is(SafeParse.stringToDouble("12 U/h")));

        // strings are not parsable
        assertThat(0.0, is(SafeParse.stringToDouble("ala ma kota")));

        // separator errors
        assertThat(0.0, is(SafeParse.stringToDouble("0.1234.5678")));
        assertThat(0.0, is(SafeParse.stringToDouble("0,1234,5678")));

        // various emptiness
        assertThat(0.0, is(SafeParse.stringToDouble("")));
        assertThat(0.0, is(SafeParse.stringToDouble("     ")));
        assertThat(0.0, is(SafeParse.stringToDouble("\n\r")));
    }

    @Test
    public void stringToIntTest() {
        // correct values
        assertThat(1052934, is(SafeParse.stringToInt("1052934")));
        assertThat(-42, is(SafeParse.stringToInt("-42")));
        assertThat(2147483647, is(SafeParse.stringToInt("2147483647")));
        assertThat(-2147483648, is(SafeParse.stringToInt("-2147483648")));

        // outside Integer range
        assertThat(0, is(SafeParse.stringToInt("2147483648")));
        assertThat(0, is(SafeParse.stringToInt("-2147483649")));

        // units or other extra values are not permitted
        assertThat(0, is(SafeParse.stringToInt("12 U/h")));
        assertThat(0, is(SafeParse.stringToInt("0.1234")));
        assertThat(0, is(SafeParse.stringToInt("0,1234")));
        assertThat(0, is(SafeParse.stringToInt(".5436564812")));
        assertThat(0, is(SafeParse.stringToInt(",5436564812")));
        assertThat(0, is(SafeParse.stringToInt("42.1234")));
        assertThat(0, is(SafeParse.stringToInt("42,1234")));
        assertThat(0, is(SafeParse.stringToInt("3212.5436564812")));
        assertThat(0, is(SafeParse.stringToInt("3212,5436564812")));
        assertThat(0, is(SafeParse.stringToInt("1000500100900")));

        // strings are not parsable
        assertThat(0, is(SafeParse.stringToInt("ala ma kota")));

        // various emptiness
        assertThat(0, is(SafeParse.stringToInt("")));
        assertThat(0, is(SafeParse.stringToInt("     ")));
        assertThat(0, is(SafeParse.stringToInt("\n\r")));
    }

    @Test
    public void stringToLongTest() {
        // correct values
        assertThat(1052934L, is(SafeParse.stringToLong("1052934")));
        assertThat(-42L, is(SafeParse.stringToLong("-42")));
        assertThat(2147483647L, is(SafeParse.stringToLong("2147483647")));
        assertThat(-2147483648L, is(SafeParse.stringToLong("-2147483648")));
        assertThat(1000500100900L, is(SafeParse.stringToLong("1000500100900")));

        // outside Integer range
        assertThat(2147483648L, is(SafeParse.stringToLong("2147483648")));
        assertThat(-2147483649L, is(SafeParse.stringToLong("-2147483649")));

        // units or other extra values are not permitted
        assertThat(0L, is(SafeParse.stringToLong("12 U/h")));
        assertThat(0L, is(SafeParse.stringToLong("0.1234")));
        assertThat(0L, is(SafeParse.stringToLong("0,1234")));
        assertThat(0L, is(SafeParse.stringToLong(".5436564812")));
        assertThat(0L, is(SafeParse.stringToLong(",5436564812")));
        assertThat(0L, is(SafeParse.stringToLong("42.1234")));
        assertThat(0L, is(SafeParse.stringToLong("42,1234")));
        assertThat(0L, is(SafeParse.stringToLong("3212.5436564812")));
        assertThat(0L, is(SafeParse.stringToLong("3212,5436564812")));

        // strings are not parsable
        assertThat(0L, is(SafeParse.stringToLong("ala ma kota")));

        // various emptiness
        assertThat(0L, is(SafeParse.stringToLong("")));
        assertThat(0L, is(SafeParse.stringToLong("     ")));
        assertThat(0L, is(SafeParse.stringToLong("\n\r")));
    }

    @Test(expected=NullPointerException.class)
    public void stringToDoubleNullTest() {
        SafeParse.stringToDouble(null);
    }

    @Test(expected=NullPointerException.class)
    public void stringToIntNullTest() {
        SafeParse.stringToInt(null);
    }

    @Test(expected=NullPointerException.class)
    public void stringToLongNullTest() {
        SafeParse.stringToLong(null);
    }

    @Before
    public void prepareMock() {

    }
}
