package app.aaps.plugins.dexcomoneplus.identity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusGs1ApplicatorParserTest {

    @Test
    fun bareFourDigitPin() {
        val id = OnePlusGs1ApplicatorParser.parse("1234")
        assertThat(id).isNotNull()
        assertThat(id!!.pin).isEqualTo("1234")
        assertThat(id.serial).isNull()
    }

    @Test
    fun gs1WithPinAndSerial() {
        // Synthetic GS1: 01(GTIN14) + 21(serial) + 240(pin)
        val raw = "\u001d010123456789012321ABCD12345678\u001d2401234"
        val id = OnePlusGs1ApplicatorParser.parse(raw)
        assertThat(id).isNotNull()
        assertThat(id!!.pin).isEqualTo("1234")
        assertThat(id.serial).isEqualTo("ABCD12345678")
        assertThat(id.gtin).isEqualTo("01234567890123")
    }

    @Test
    fun caretBracketGsNormalized() {
        val raw = "^]010123456789012321SERIALXYZ001^]2409876"
        val id = OnePlusGs1ApplicatorParser.parse(raw)
        assertThat(id).isNotNull()
        assertThat(id!!.pin).isEqualTo("9876")
        assertThat(id.serial).isEqualTo("SERIALXYZ001")
    }

    @Test
    fun extractPinTakesTrailingFourDigits() {
        assertThat(OnePlusGs1ApplicatorParser.extractPin("XX1234")).isEqualTo("1234")
        assertThat(OnePlusGs1ApplicatorParser.extractPin("12")).isNull()
    }
}
