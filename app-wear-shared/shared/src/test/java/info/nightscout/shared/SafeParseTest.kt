package info.nightscout.shared

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
class SafeParseTest {

    @Test fun stringToDoubleTest() {
        // correct values
        Assertions.assertEquals(0.1234, SafeParse.stringToDouble("0.1234"), 0.001)
        Assertions.assertEquals(0.1234, SafeParse.stringToDouble("0,1234"), 0.001)
        Assertions.assertEquals(0.5436564812, SafeParse.stringToDouble(".5436564812"), 0.001)
        Assertions.assertEquals(0.5436564812, SafeParse.stringToDouble(",5436564812"), 0.001)
        Assertions.assertEquals(1000500100900.0, SafeParse.stringToDouble("1000500100900"), 0.001)
        Assertions.assertEquals(42.0, SafeParse.stringToDouble("42"), 0.001)

        // units or other extra values are not permitted
        Assertions.assertEquals(0.0, SafeParse.stringToDouble("12 U/h"), 0.001)

        // strings are not parsable
        Assertions.assertEquals(0.0, SafeParse.stringToDouble("ala ma kota"), 0.001)

        // separator errors
        Assertions.assertEquals(0.0, SafeParse.stringToDouble("0.1234.5678"), 0.001)
        Assertions.assertEquals(0.0, SafeParse.stringToDouble("0,1234,5678"), 0.001)

        // various emptiness
        Assertions.assertEquals(0.0, SafeParse.stringToDouble(""), 0.001)
        Assertions.assertEquals(0.0, SafeParse.stringToDouble("     "), 0.001)
        Assertions.assertEquals(0.0, SafeParse.stringToDouble("\n\r"), 0.001)
    }

    @Test fun stringToIntTest() {
        // correct values
        Assertions.assertEquals(1052934, SafeParse.stringToInt("1052934"))
        Assertions.assertEquals(-42, SafeParse.stringToInt("-42"))
        Assertions.assertEquals(2147483647, SafeParse.stringToInt("2147483647"))
        Assertions.assertEquals(-2147483648, SafeParse.stringToInt("-2147483648"))

        // outside Integer range
        Assertions.assertEquals(0, SafeParse.stringToInt("2147483648"))
        Assertions.assertEquals(0, SafeParse.stringToInt("-2147483649"))

        // units or other extra values are not permitted
        Assertions.assertEquals(0, SafeParse.stringToInt("12 U/h"))
        Assertions.assertEquals(0, SafeParse.stringToInt("0.1234"))
        Assertions.assertEquals(0, SafeParse.stringToInt("0,1234"))
        Assertions.assertEquals(0, SafeParse.stringToInt(".5436564812"))
        Assertions.assertEquals(0, SafeParse.stringToInt(",5436564812"))
        Assertions.assertEquals(0, SafeParse.stringToInt("42.1234"))
        Assertions.assertEquals(0, SafeParse.stringToInt("42,1234"))
        Assertions.assertEquals(0, SafeParse.stringToInt("3212.5436564812"))
        Assertions.assertEquals(0, SafeParse.stringToInt("3212,5436564812"))
        Assertions.assertEquals(0, SafeParse.stringToInt("1000500100900"))

        // strings are not parsable
        Assertions.assertEquals(0, SafeParse.stringToInt("ala ma kota"))

        // various emptiness
        Assertions.assertEquals(0, SafeParse.stringToInt(""))
        Assertions.assertEquals(0, SafeParse.stringToInt("     "))
        Assertions.assertEquals(0, SafeParse.stringToInt("\n\r"))
    }

    @Test fun stringToLongTest() {
        // correct values
        Assertions.assertEquals(1052934L, SafeParse.stringToLong("1052934"))
        Assertions.assertEquals(-42L, SafeParse.stringToLong("-42"))
        Assertions.assertEquals(2147483647L, SafeParse.stringToLong("2147483647"))
        Assertions.assertEquals(-2147483648L, SafeParse.stringToLong("-2147483648"))
        Assertions.assertEquals(1000500100900L, SafeParse.stringToLong("1000500100900"))

        // outside Integer range
        Assertions.assertEquals(2147483648L, SafeParse.stringToLong("2147483648"))
        Assertions.assertEquals(-2147483649L, SafeParse.stringToLong("-2147483649"))

        // units or other extra values are not permitted
        Assertions.assertEquals(0L, SafeParse.stringToLong("12 U/h"))
        Assertions.assertEquals(0L, SafeParse.stringToLong("0.1234"))
        Assertions.assertEquals(0L, SafeParse.stringToLong("0,1234"))
        Assertions.assertEquals(0L, SafeParse.stringToLong(".5436564812"))
        Assertions.assertEquals(0L, SafeParse.stringToLong(",5436564812"))
        Assertions.assertEquals(0L, SafeParse.stringToLong("42.1234"))
        Assertions.assertEquals(0L, SafeParse.stringToLong("42,1234"))
        Assertions.assertEquals(0L, SafeParse.stringToLong("3212.5436564812"))
        Assertions.assertEquals(0L, SafeParse.stringToLong("3212,5436564812"))

        // strings are not parsable
        Assertions.assertEquals(0L, SafeParse.stringToLong("ala ma kota"))

        // various emptiness
        Assertions.assertEquals(0L, SafeParse.stringToLong(""))
        Assertions.assertEquals(0L, SafeParse.stringToLong("     "))
        Assertions.assertEquals(0L, SafeParse.stringToLong("\n\r"))
    }

    @Test
    fun stringToDoubleNullTest() {
        Assertions.assertEquals(0.0, SafeParse.stringToDouble(null), 0.001)
    }

    @Test
    fun stringToIntNullTest() {
        Assertions.assertEquals(0, SafeParse.stringToInt(null))
    }

    @Test
    fun stringToLongNullTest() {
        Assertions.assertEquals(0L, SafeParse.stringToLong(null))
    }
}