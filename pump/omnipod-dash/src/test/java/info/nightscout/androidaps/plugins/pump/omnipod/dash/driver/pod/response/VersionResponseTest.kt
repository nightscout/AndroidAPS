package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class VersionResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("0115040A00010300040208146CC1000954D400FFFFFFFF")
        val response = VersionResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.ActivationResponseType.GET_VERSION_RESPONSE, response.activationResponseType)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE.value, response.messageType)
        Assert.assertEquals(21.toShort(), response.messageLength)
        Assert.assertEquals(4.toShort(), response.firmwareVersionMajor)
        Assert.assertEquals(10.toShort(), response.firmwareVersionMinor)
        Assert.assertEquals(0.toShort(), response.firmwareVersionInterim)
        Assert.assertEquals(1.toShort(), response.bleVersionMajor)
        Assert.assertEquals(3.toShort(), response.bleVersionMinor)
        Assert.assertEquals(0.toShort(), response.bleVersionInterim)
        Assert.assertEquals(4.toShort(), response.productId)
        Assert.assertEquals(PodStatus.FILLED, response.podStatus)
        Assert.assertEquals(135556289L, response.lotNumber)
        Assert.assertEquals(611540L, response.podSequenceNumber)
        Assert.assertEquals(0.toByte(), response.rssi)
        Assert.assertEquals(0.toByte(), response.receiverLowerGain)
        Assert.assertEquals(4294967295L, response.uniqueIdReceivedInCommand)
    }
}
