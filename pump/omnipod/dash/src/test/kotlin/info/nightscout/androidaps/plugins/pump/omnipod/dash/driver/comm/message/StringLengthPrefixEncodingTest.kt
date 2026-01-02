package app.aaps.pump.omnipod.dash.driver.comm.message

import app.aaps.core.utils.toHex
import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Hex
import org.junit.jupiter.api.Test

class StringLengthPrefixEncodingTest {

    private val p0Payload = Hex.decode("50,30,3d,00,01,a5".replace(",", "")) // from logs
    private val p0Content = Hex.decode("a5")

    @Test fun testFormatKeysP0() {
        val payload = StringLengthPrefixEncoding.formatKeys(arrayOf("P0="), arrayOf(p0Content))
        assertThat(p0Payload.toHex()).isEqualTo(payload.toHex())
    }

    @Test fun testParseKeysP0() {
        val parsed = StringLengthPrefixEncoding.parseKeys(arrayOf("P0="), p0Payload)
        assertThat(parsed).hasLength(1)
        assertThat(parsed[0].toHex()).isEqualTo(p0Content.toHex())
    }
}
