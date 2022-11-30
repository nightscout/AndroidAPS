package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class SetUniqueIdResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("011B13881008340A50040A00010300040308146CC1000954D402420001")
        val response = SetUniqueIdResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE, response.activationResponseType)
        Assert.assertEquals(ResponseType.ACTIVATION_RESPONSE.value, response.messageType)
        Assert.assertEquals(27.toShort(), response.messageLength)
        Assert.assertEquals(5000.toShort(), response.pulseVolumeInTenThousandthMicroLiter)
        Assert.assertEquals(16.toShort(), response.pumpRate)
        Assert.assertEquals(8.toShort(), response.primePumpRate)
        Assert.assertEquals(52.toShort(), response.numberOfEngagingClutchDrivePulses)
        Assert.assertEquals(10.toShort(), response.numberOfPrimePulses)
        Assert.assertEquals(80.toShort(), response.podExpirationTimeInHours)
        Assert.assertEquals(4.toShort(), response.firmwareVersionMajor)
        Assert.assertEquals(10.toShort(), response.firmwareVersionMinor)
        Assert.assertEquals(0.toShort(), response.firmwareVersionInterim)
        Assert.assertEquals(1.toShort(), response.bleVersionMajor)
        Assert.assertEquals(3.toShort(), response.bleVersionMinor)
        Assert.assertEquals(0.toShort(), response.bleVersionInterim)
        Assert.assertEquals(4.toShort(), response.productId)
        Assert.assertEquals(PodStatus.UID_SET, response.podStatus)
        Assert.assertEquals(135556289L, response.lotNumber)
        Assert.assertEquals(611540L, response.podSequenceNumber)
        Assert.assertEquals(37879809L, response.uniqueIdReceivedInCommand)
    }
}
