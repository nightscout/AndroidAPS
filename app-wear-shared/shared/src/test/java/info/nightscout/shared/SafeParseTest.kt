package info.nightscout.shared

import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
class SafeParseTest {

    @Test fun stringToDoubleTest() {
        // correct values
        assertEquals(0.1234, SafeParse.stringToDouble("0.1234"), 0.001)
        assertEquals(0.1234, SafeParse.stringToDouble("0,1234"), 0.001)
        assertEquals(0.5436564812, SafeParse.stringToDouble(".5436564812"), 0.001)
        assertEquals(0.5436564812, SafeParse.stringToDouble(",5436564812"), 0.001)
        assertEquals(1000500100900.0, SafeParse.stringToDouble("1000500100900"), 0.001)
        assertEquals(42.0, SafeParse.stringToDouble("42"), 0.001)

        // units or other extra values are not permitted
        assertEquals(0.0, SafeParse.stringToDouble("12 U/h"), 0.001)

        // strings are not parsable
        assertEquals(0.0, SafeParse.stringToDouble("ala ma kota"), 0.001)

        // separator errors
        assertEquals(0.0, SafeParse.stringToDouble("0.1234.5678"), 0.001)
        assertEquals(0.0, SafeParse.stringToDouble("0,1234,5678"), 0.001)

        // various emptiness
        assertEquals(0.0, SafeParse.stringToDouble(""), 0.001)
        assertEquals(0.0, SafeParse.stringToDouble("     "), 0.001)
        assertEquals(0.0, SafeParse.stringToDouble("\n\r"), 0.001)
    }

    @Test fun stringToIntTest() {
        // correct values
        assertEquals(1052934, SafeParse.stringToInt("1052934"))
        assertEquals(-42, SafeParse.stringToInt("-42"))
        assertEquals(2147483647, SafeParse.stringToInt("2147483647"))
        assertEquals(-2147483648, SafeParse.stringToInt("-2147483648"))

        // outside Integer range
        assertEquals(0, SafeParse.stringToInt("2147483648"))
        assertEquals(0, SafeParse.stringToInt("-2147483649"))

        // units or other extra values are not permitted
        assertEquals(0, SafeParse.stringToInt("12 U/h"))
        assertEquals(0, SafeParse.stringToInt("0.1234"))
        assertEquals(0, SafeParse.stringToInt("0,1234"))
        assertEquals(0, SafeParse.stringToInt(".5436564812"))
        assertEquals(0, SafeParse.stringToInt(",5436564812"))
        assertEquals(0, SafeParse.stringToInt("42.1234"))
        assertEquals(0, SafeParse.stringToInt("42,1234"))
        assertEquals(0, SafeParse.stringToInt("3212.5436564812"))
        assertEquals(0, SafeParse.stringToInt("3212,5436564812"))
        assertEquals(0, SafeParse.stringToInt("1000500100900"))

        // strings are not parsable
        assertEquals(0, SafeParse.stringToInt("ala ma kota"))

        // various emptiness
        assertEquals(0, SafeParse.stringToInt(""))
        assertEquals(0, SafeParse.stringToInt("     "))
        assertEquals(0, SafeParse.stringToInt("\n\r"))
    }

    @Test fun stringToLongTest() {
        // correct values
        assertEquals(1052934L, SafeParse.stringToLong("1052934"))
        assertEquals(-42L, SafeParse.stringToLong("-42"))
        assertEquals(2147483647L, SafeParse.stringToLong("2147483647"))
        assertEquals(-2147483648L, SafeParse.stringToLong("-2147483648"))
        assertEquals(1000500100900L, SafeParse.stringToLong("1000500100900"))

        // outside Integer range
        assertEquals(2147483648L, SafeParse.stringToLong("2147483648"))
        assertEquals(-2147483649L, SafeParse.stringToLong("-2147483649"))

        // units or other extra values are not permitted
        assertEquals(0L, SafeParse.stringToLong("12 U/h"))
        assertEquals(0L, SafeParse.stringToLong("0.1234"))
        assertEquals(0L, SafeParse.stringToLong("0,1234"))
        assertEquals(0L, SafeParse.stringToLong(".5436564812"))
        assertEquals(0L, SafeParse.stringToLong(",5436564812"))
        assertEquals(0L, SafeParse.stringToLong("42.1234"))
        assertEquals(0L, SafeParse.stringToLong("42,1234"))
        assertEquals(0L, SafeParse.stringToLong("3212.5436564812"))
        assertEquals(0L, SafeParse.stringToLong("3212,5436564812"))

        // strings are not parsable
        assertEquals(0L, SafeParse.stringToLong("ala ma kota"))

        // various emptiness
        assertEquals(0L, SafeParse.stringToLong(""))
        assertEquals(0L, SafeParse.stringToLong("     "))
        assertEquals(0L, SafeParse.stringToLong("\n\r"))
    }

    @Test
    fun stringToDoubleNullTest() {
        assertEquals(0.0, SafeParse.stringToDouble(null), 0.001)
    }

    @Test
    fun stringToIntNullTest() {
        assertEquals(0, SafeParse.stringToInt(null))
    }

    @Test
    fun stringToLongNullTest() {
        assertEquals(0L, SafeParse.stringToLong(null))
    }
}