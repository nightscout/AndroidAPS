package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test

class VersionResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("0115040A00010300040208146CC1000954D400FFFFFFFF")
        val response = VersionResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.ActivationResponseType.GET_VERSION_RESPONSE, response.activationResponseType)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE.value, response.getMessageType())
        Assert.assertEquals(21.toShort(), response.getMessageLength())
        Assert.assertEquals(4.toShort(), response.getFirmwareVersionMajor())
        Assert.assertEquals(10.toShort(), response.getFirmwareVersionMinor())
        Assert.assertEquals(0.toShort(), response.getFirmwareVersionInterim())
        Assert.assertEquals(1.toShort(), response.getBleVersionMajor())
        Assert.assertEquals(3.toShort(), response.getBleVersionMinor())
        Assert.assertEquals(0.toShort(), response.getBleVersionInterim())
        Assert.assertEquals(4.toShort(), response.getProductId())
        Assert.assertEquals(PodStatus.FILLED, response.getPodStatus())
        Assert.assertEquals(135556289L, response.getLotNumber())
        Assert.assertEquals(611540L, response.getPodSequenceNumber())
        Assert.assertEquals(0.toByte(), response.getRssi())
        Assert.assertEquals(0.toByte(), response.getReceiverLowerGain())
        Assert.assertEquals(4294967295L, response.getUniqueIdReceivedInCommand())
    }
}