package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DeactivateCommandTest {

    @Test @Throws(DecoderException::class) fun testEncoding() {
        val encoded = DeactivateCommand.Builder()
            .setUniqueId(37879809)
            .setSequenceNumber(5.toShort())
            .setNonce(1229869870)
            .build()
            .encoded

        Assertions.assertArrayEquals(Hex.decodeHex("0242000114061C04494E532E001C"), encoded)
    }
}
