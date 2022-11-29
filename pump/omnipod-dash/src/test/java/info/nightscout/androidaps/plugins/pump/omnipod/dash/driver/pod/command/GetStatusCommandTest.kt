package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class GetStatusCommandTest {

    @Test @Throws(DecoderException::class) fun testGetDefaultStatusResponse() {
        val encoded = GetStatusCommand.Builder()
            .setUniqueId(37879810)
            .setSequenceNumber(15.toShort())
            .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
            .build()
            .encoded

        Assert.assertArrayEquals(Hex.decodeHex("024200023C030E0100024C"), encoded)
    }
}
