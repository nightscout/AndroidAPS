package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.NakErrorType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class NakResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("0603070009")
        val response = NakResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.NAK_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.NAK_RESPONSE.value, response.messageType)
        Assert.assertEquals(NakErrorType.ILLEGAL_PARAM, response.nakErrorType)
        Assert.assertEquals(AlarmType.NONE, response.alarmType)
        Assert.assertEquals(PodStatus.RUNNING_BELOW_MIN_VOLUME, response.podStatus)
        Assert.assertEquals(0x00.toShort(), response.securityNakSyncCount)
    }
}
