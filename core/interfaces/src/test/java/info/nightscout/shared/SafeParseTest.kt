package info.nightscout.shared

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
class SafeParseTest {

    @Test fun stringToDoubleTest() {
        // correct values
        assertThat(SafeParse.stringToDouble("0.1234")).isWithin(0.001).of(0.1234)
        assertThat(SafeParse.stringToDouble("0,1234")).isWithin(0.001).of(0.1234)
        assertThat(SafeParse.stringToDouble(".5436564812")).isWithin(0.001).of(0.5436564812)
        assertThat(SafeParse.stringToDouble(",5436564812")).isWithin(0.001).of(0.5436564812)
        assertThat(SafeParse.stringToDouble("1000500100900")).isWithin(0.001).of(1000500100900.0)
        assertThat(SafeParse.stringToDouble("42")).isWithin(0.001).of(42.0)

        // units or other extra values are not permitted
        assertThat(SafeParse.stringToDouble("12 U/h")).isWithin(0.001).of(0.0)

        // strings are not parsable
        assertThat(SafeParse.stringToDouble("ala ma kota")).isWithin(0.001).of(0.0)

        // separator errors
        assertThat(SafeParse.stringToDouble("0.1234.5678")).isWithin(0.001).of(0.0)
        assertThat(SafeParse.stringToDouble("0,1234,5678")).isWithin(0.001).of(0.0)

        // various emptiness
        assertThat(SafeParse.stringToDouble("")).isWithin(0.001).of(0.0)
        assertThat(SafeParse.stringToDouble("     ")).isWithin(0.001).of(0.0)
        assertThat(SafeParse.stringToDouble("\n\r")).isWithin(0.001).of(0.0)
    }

    @Test fun stringToIntTest() {
        // correct values
        assertThat(SafeParse.stringToInt("1052934")).isEqualTo(1052934)
        assertThat(SafeParse.stringToInt("-42")).isEqualTo(-42)
        assertThat(SafeParse.stringToInt("2147483647")).isEqualTo(2147483647)
        assertThat(SafeParse.stringToInt("-2147483648")).isEqualTo(-2147483648)

        // outside Integer range
        assertThat(SafeParse.stringToInt("2147483648")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("-2147483649")).isEqualTo(0)

        // units or other extra values are not permitted
        assertThat(SafeParse.stringToInt("12 U/h")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("0.1234")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("0,1234")).isEqualTo(0)
        assertThat(SafeParse.stringToInt(".5436564812")).isEqualTo(0)
        assertThat(SafeParse.stringToInt(",5436564812")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("42.1234")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("42,1234")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("3212.5436564812")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("3212,5436564812")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("1000500100900")).isEqualTo(0)

        // strings are not parsable
        assertThat(SafeParse.stringToInt("ala ma kota")).isEqualTo(0)

        // various emptiness
        assertThat(SafeParse.stringToInt("")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("     ")).isEqualTo(0)
        assertThat(SafeParse.stringToInt("\n\r")).isEqualTo(0)
    }

    @Test fun stringToLongTest() {
        // correct values
        assertThat(SafeParse.stringToLong("1052934")).isEqualTo(1052934L)
        assertThat(SafeParse.stringToLong("-42")).isEqualTo(-42L)
        assertThat(SafeParse.stringToLong("2147483647")).isEqualTo(2147483647L)
        assertThat(SafeParse.stringToLong("-2147483648")).isEqualTo(-2147483648L)
        assertThat(SafeParse.stringToLong("1000500100900")).isEqualTo(1000500100900L)

        // outside Integer range
        assertThat(SafeParse.stringToLong("2147483648")).isEqualTo(2147483648L)
        assertThat(SafeParse.stringToLong("-2147483649")).isEqualTo(-2147483649L)

        // units or other extra values are not permitted
        assertThat(SafeParse.stringToLong("12 U/h")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("0.1234")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("0,1234")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong(".5436564812")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong(",5436564812")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("42.1234")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("42,1234")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("3212.5436564812")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("3212,5436564812")).isEqualTo(0L)

        // strings are not parsable
        assertThat(SafeParse.stringToLong("ala ma kota")).isEqualTo(0L)

        // various emptiness
        assertThat(SafeParse.stringToLong("")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("     ")).isEqualTo(0L)
        assertThat(SafeParse.stringToLong("\n\r")).isEqualTo(0L)
    }

    @Test
    fun stringToDoubleNullTest() {
        assertThat(SafeParse.stringToDouble(null)).isWithin(0.001).of(0.0)
    }

    @Test
    fun stringToIntNullTest() {
        assertThat(SafeParse.stringToInt(null)).isEqualTo(0)
    }

    @Test
    fun stringToLongNullTest() {
        assertThat(SafeParse.stringToLong(null)).isEqualTo(0L)
    }
}
