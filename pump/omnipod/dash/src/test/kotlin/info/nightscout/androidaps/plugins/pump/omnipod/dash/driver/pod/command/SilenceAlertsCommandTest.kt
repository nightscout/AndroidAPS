package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test
import java.util.EnumSet

class SilenceAlertsCommandTest {

    @Test fun testSilenceLowReservoirAlert() {
        val encoded = SilenceAlertsCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(1.toShort())
            .setNonce(1229869870)
            .setAlertTypes(EnumSet.of(AlertType.LOW_RESERVOIR))
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("0242000304071105494E532E1081CE").asList()).inOrder()
    }

    // TODO capture more silence alerts commands
}
