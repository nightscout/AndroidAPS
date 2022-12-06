package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.util.Date

class SetUniqueIdCommandTest {

    @Test @Throws(DecoderException::class) fun testEncoding() {
        @Suppress("DEPRECATION") val encoded = SetUniqueIdCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(6.toShort())
            .setLotNumber(135556289)
            .setPodSequenceNumber(681767)
            .setInitializationTime(Date(2021, 1, 10, 14, 41))
            .build()
            .encoded

        Assert.assertArrayEquals(Hex.decodeHex("FFFFFFFF18150313024200031404020A150E2908146CC1000A67278344"), encoded)
    }
}
