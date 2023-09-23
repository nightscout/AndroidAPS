package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StopDeliveryCommandTest {

    @Test @Throws(DecoderException::class) fun testStopTempBasal() {
        val encoded = StopDeliveryCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(0.toShort())
            .setNonce(1229869870)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.TEMP_BASAL)
            .setBeepType(BeepType.LONG_SINGLE_BEEP)
            .build()
            .encoded

        Assertions.assertArrayEquals(Hex.decodeHex("0242000300071F05494E532E6201B1"), encoded)
    }

    @Test @Throws(DecoderException::class) fun testSuspendDelivery() {
        val encoded = StopDeliveryCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(2.toShort())
            .setNonce(1229869870)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.ALL)
            .setBeepType(BeepType.SILENT)
            .build()
            .encoded

        Assertions.assertArrayEquals(Hex.decodeHex("0242000308071F05494E532E078287"), encoded)
    }

    // TODO test cancel bolus
}
