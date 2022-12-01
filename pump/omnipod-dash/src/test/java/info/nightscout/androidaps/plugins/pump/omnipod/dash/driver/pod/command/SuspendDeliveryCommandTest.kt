package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class SuspendDeliveryCommandTest {
    @Test @Throws(DecoderException::class) fun testSuspendDelivery() {
        val encoded = SuspendDeliveryCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(0.toShort())
            .setNonce(1229869870)
            .setBeepType(BeepType.LONG_SINGLE_BEEP)
            .build()
            .encoded
        Assert.assertArrayEquals(Hex.decodeHex("0242000300131f05494e532e67190a494e532e680000140302811f"), encoded)
    }
}
