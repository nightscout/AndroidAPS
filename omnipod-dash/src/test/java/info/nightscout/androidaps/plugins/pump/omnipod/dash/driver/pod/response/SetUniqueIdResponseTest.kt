package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test

class SetUniqueIdResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("011B13881008340A50040A00010300040308146CC1000954D402420001")
        val response = SetUniqueIdResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE, response.activationResponseType)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE.value, response.getMessageType())
        Assert.assertEquals(27.toShort(), response.getMessageLength())
        Assert.assertEquals(5000.toShort(), response.getPulseVolumeInTenThousandthMicroLiter())
        Assert.assertEquals(16.toShort(), response.getDeliveryRate())
        Assert.assertEquals(8.toShort(), response.getPrimeRate())
        Assert.assertEquals(52.toShort(), response.getNumberOfEngagingClutchDrivePulses())
        Assert.assertEquals(10.toShort(), response.getNumberOfPrimePulses())
        Assert.assertEquals(80.toShort(), response.getPodExpirationTimeInHours())
        Assert.assertEquals(4.toShort(), response.getFirmwareVersionMajor())
        Assert.assertEquals(10.toShort(), response.getFirmwareVersionMinor())
        Assert.assertEquals(0.toShort(), response.getFirmwareVersionInterim())
        Assert.assertEquals(1.toShort(), response.getBleVersionMajor())
        Assert.assertEquals(3.toShort(), response.getBleVersionMinor())
        Assert.assertEquals(0.toShort(), response.getBleVersionInterim())
        Assert.assertEquals(4.toShort(), response.getProductId())
        Assert.assertEquals(PodStatus.UID_SET, response.getPodStatus())
        Assert.assertEquals(135556289L, response.getLotNumber())
        Assert.assertEquals(611540L, response.getPodSequenceNumber())
        Assert.assertEquals(37879809L, response.getUniqueIdReceivedInCommand())
    }
}