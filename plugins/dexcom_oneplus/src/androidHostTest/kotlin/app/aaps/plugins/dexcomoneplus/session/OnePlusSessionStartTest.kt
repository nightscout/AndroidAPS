package app.aaps.plugins.dexcomoneplus.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusSessionStartTest {

    @Test
    fun `accepts 4 digit pairing code`() {
        assertThat(OnePlusSessionStart.isValidPairingCode("1234")).isTrue()
        assertThat(OnePlusSessionStart.isValidPairingCode(" 5678 ")).isTrue()
        assertThat(OnePlusSessionStart.validationError("1234")).isNull()
    }

    @Test
    fun `rejects non digit or wrong length`() {
        assertThat(OnePlusSessionStart.isValidPairingCode("12")).isFalse()
        assertThat(OnePlusSessionStart.isValidPairingCode("12345")).isFalse()
        assertThat(OnePlusSessionStart.isValidPairingCode("12ab")).isFalse()
        assertThat(OnePlusSessionStart.validationError("abcd")).isNotNull()
    }

    @Test
    fun `rejects reserved short pairing codes from xDrip list`() {
        assertThat(OnePlusSessionStart.isValidPairingCode("0006")).isFalse()
        assertThat(OnePlusSessionStart.isValidPairingCode("6666")).isFalse()
        assertThat(OnePlusSessionStart.isValidPairingCode("9999")).isFalse()
        assertThat(OnePlusSessionStart.validationError("0006")).contains("reserved")
    }
}
