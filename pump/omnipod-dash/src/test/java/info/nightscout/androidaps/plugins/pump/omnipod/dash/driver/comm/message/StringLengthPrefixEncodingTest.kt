package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import com.google.crypto.tink.subtle.Hex
import info.nightscout.core.utils.toHex
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class StringLengthPrefixEncodingTest {

    private val p0Payload = Hex.decode("50,30,3d,00,01,a5".replace(",", "")) // from logs
    private val p0Content = Hex.decode("a5")

    @Test fun testFormatKeysP0() {
        val payload = StringLengthPrefixEncoding.formatKeys(arrayOf("P0="), arrayOf(p0Content))
        assertEquals(p0Payload.toHex(), payload.toHex())
    }

    @Test fun testParseKeysP0() {
        val parsed = StringLengthPrefixEncoding.parseKeys(arrayOf("P0="), p0Payload)
        assertEquals(parsed.size, 1)
        assertEquals(parsed[0].toHex(), p0Content.toHex())
    }
}
