package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class GetStatusCommandTest {

    @Test fun testGetDefaultStatusResponse() {
        val encoded = GetStatusCommand.Builder()
            .setUniqueId(37879810)
            .setSequenceNumber(15.toShort())
            .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
            .build()
            .encoded

        assertThat(encoded).asList().containsExactlyElementsIn(Hex.decodeHex("024200023C030E0100024C").asList()).inOrder()
    }
}
