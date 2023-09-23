package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.NakErrorType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class NakResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("0603070009")
        val response = NakResponse(encoded)

        Assertions.assertArrayEquals(encoded, response.encoded)
        Assertions.assertNotSame(encoded, response.encoded)
        Assertions.assertEquals(ResponseType.NAK_RESPONSE, response.responseType)
        Assertions.assertEquals(ResponseType.NAK_RESPONSE.value, response.messageType)
        Assertions.assertEquals(NakErrorType.ILLEGAL_PARAM, response.nakErrorType)
        Assertions.assertEquals(AlarmType.NONE, response.alarmType)
        Assertions.assertEquals(PodStatus.RUNNING_BELOW_MIN_VOLUME, response.podStatus)
        Assertions.assertEquals(0x00.toShort(), response.securityNakSyncCount)
    }
}
