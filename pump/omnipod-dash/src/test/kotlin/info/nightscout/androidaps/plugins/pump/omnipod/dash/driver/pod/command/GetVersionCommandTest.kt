package app.aaps.pump.omnipod.dash.driver.pod.command

import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class GetVersionCommandTest {

    @Test fun testEncoding() {
        val encoded = GetVersionCommand.Builder()
            .setSequenceNumber(0.toShort())
            .setUniqueId(GetVersionCommand.DEFAULT_UNIQUE_ID)
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("FFFFFFFF00060704FFFFFFFF82B2").asList()).inOrder()
    }
}
