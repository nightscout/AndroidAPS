package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class VersionResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("0115040A00010300040208146CC1000954D400FFFFFFFF")
        val response = VersionResponse(encoded)

        Assertions.assertArrayEquals(encoded, response.encoded)
        Assertions.assertNotSame(encoded, response.encoded)
        Assertions.assertEquals(ResponseType.ACTIVATION_RESPONSE, response.responseType)
        Assertions.assertEquals(ResponseType.ActivationResponseType.GET_VERSION_RESPONSE, response.activationResponseType)
        Assertions.assertEquals(ResponseType.ACTIVATION_RESPONSE.value, response.messageType)
        Assertions.assertEquals(21.toShort(), response.messageLength)
        Assertions.assertEquals(4.toShort(), response.firmwareVersionMajor)
        Assertions.assertEquals(10.toShort(), response.firmwareVersionMinor)
        Assertions.assertEquals(0.toShort(), response.firmwareVersionInterim)
        Assertions.assertEquals(1.toShort(), response.bleVersionMajor)
        Assertions.assertEquals(3.toShort(), response.bleVersionMinor)
        Assertions.assertEquals(0.toShort(), response.bleVersionInterim)
        Assertions.assertEquals(4.toShort(), response.productId)
        Assertions.assertEquals(PodStatus.FILLED, response.podStatus)
        Assertions.assertEquals(135556289L, response.lotNumber)
        Assertions.assertEquals(611540L, response.podSequenceNumber)
        Assertions.assertEquals(0.toByte(), response.rssi)
        Assertions.assertEquals(0.toByte(), response.receiverLowerGain)
        Assertions.assertEquals(4294967295L, response.uniqueIdReceivedInCommand)
    }
}
