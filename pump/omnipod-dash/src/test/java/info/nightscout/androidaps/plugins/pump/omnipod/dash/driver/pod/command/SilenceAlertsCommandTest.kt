package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.EnumSet

class SilenceAlertsCommandTest {

    @Test @Throws(DecoderException::class) fun testSilenceLowReservoirAlert() {
        val encoded = SilenceAlertsCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(1.toShort())
            .setNonce(1229869870)
            .setAlertTypes(EnumSet.of(AlertType.LOW_RESERVOIR))
            .build()
            .encoded

        Assertions.assertArrayEquals(Hex.decodeHex("0242000304071105494E532E1081CE"), encoded)
    }

    // TODO capture more silence alerts commands
}
