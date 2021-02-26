package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.NakErrorType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test

class NakResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("0603070009")
        val response = NakResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.NAK_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.NAK_RESPONSE.value, response.getMessageType())
        Assert.assertEquals(NakErrorType.ILLEGAL_PARAM, response.getNakErrorType())
        Assert.assertEquals(AlarmType.NONE, response.getAlarmType())
        Assert.assertEquals(PodStatus.RUNNING_BELOW_MIN_VOLUME, response.getPodStatus())
        Assert.assertEquals(0x00.toShort(), response.getSecurityNakSyncCount())
    }
}