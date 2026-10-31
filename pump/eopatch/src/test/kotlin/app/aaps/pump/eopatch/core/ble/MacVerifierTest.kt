package app.aaps.pump.eopatch.core.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MacVerifierTest {

    @Test
    fun `valid MAC address should return true`() {
        assertThat(MacVerifier.isValid("AA:BB:CC:DD:EE:FF")).isTrue()
        assertThat(MacVerifier.isValid("00:11:22:33:44:55")).isTrue()
        assertThat(MacVerifier.isValid("ab:cd:ef:01:23:45")).isTrue()
    }

    @Test
    fun `null should return false`() {
        assertThat(MacVerifier.isValid(null)).isFalse()
    }

    @Test
    fun `wrong length should return false`() {
        assertThat(MacVerifier.isValid("AA:BB:CC")).isFalse()
        assertThat(MacVerifier.isValid("")).isFalse()
        assertThat(MacVerifier.isValid("AA:BB:CC:DD:EE:FF:00")).isFalse()
    }

    @Test
    fun `invalid characters should return false`() {
        assertThat(MacVerifier.isValid("GG:HH:II:JJ:KK:LL")).isFalse()
        assertThat(MacVerifier.isValid("AA-BB-CC-DD-EE-FF")).isFalse()
    }

    @Test
    fun `missing colons should return false`() {
        assertThat(MacVerifier.isValid("AABBCCDDEEFF00112")).isFalse()
    }
}
