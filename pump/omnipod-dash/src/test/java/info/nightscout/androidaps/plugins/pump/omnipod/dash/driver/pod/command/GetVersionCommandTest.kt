package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class GetVersionCommandTest {

    @Test @Throws(DecoderException::class) fun testEncoding() {
        val encoded = GetVersionCommand.Builder()
            .setSequenceNumber(0.toShort())
            .setUniqueId(GetVersionCommand.DEFAULT_UNIQUE_ID)
            .build()
            .encoded

        Assert.assertArrayEquals(Hex.decodeHex("FFFFFFFF00060704FFFFFFFF82B2"), encoded)
    }
}
