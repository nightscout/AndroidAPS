package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepType
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class StopDeliveryCommandTest {

    @Test fun testStopTempBasal() {
        val encoded = StopDeliveryCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(0.toShort())
            .setNonce(1229869870)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.TEMP_BASAL)
            .setBeepType(BeepType.LONG_SINGLE_BEEP)
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("0242000300071F05494E532E6201B1").asList()).inOrder()
    }

    @Test fun testSuspendDelivery() {
        val encoded = StopDeliveryCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(2.toShort())
            .setNonce(1229869870)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.ALL)
            .setBeepType(BeepType.SILENT)
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("0242000308071F05494E532E078287").asList()).inOrder()
    }

    // TODO test cancel bolus
}
