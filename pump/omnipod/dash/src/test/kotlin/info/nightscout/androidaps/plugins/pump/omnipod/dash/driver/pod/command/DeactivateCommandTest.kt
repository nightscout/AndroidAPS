package app.aaps.pump.omnipod.dash.driver.pod.command

import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class DeactivateCommandTest {

    @Test fun testEncoding() {
        val encoded = DeactivateCommand.Builder()
            .setUniqueId(37879809)
            .setSequenceNumber(5.toShort())
            .setNonce(1229869870)
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("0242000114061C04494E532E001C").asList()).inOrder()
    }
}
