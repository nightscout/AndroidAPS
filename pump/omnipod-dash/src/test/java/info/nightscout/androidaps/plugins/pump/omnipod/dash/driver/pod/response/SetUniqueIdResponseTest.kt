package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SetUniqueIdResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("011B13881008340A50040A00010300040308146CC1000954D402420001")
        val response = SetUniqueIdResponse(encoded)

        Assertions.assertArrayEquals(encoded, response.encoded)
        Assertions.assertNotSame(encoded, response.encoded)
        Assertions.assertEquals(ResponseType.ACTIVATION_RESPONSE, response.responseType)
        Assertions.assertEquals(ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE, response.activationResponseType)
        Assertions.assertEquals(ResponseType.ACTIVATION_RESPONSE.value, response.messageType)
        Assertions.assertEquals(27.toShort(), response.messageLength)
        Assertions.assertEquals(5000.toShort(), response.pulseVolumeInTenThousandthMicroLiter)
        Assertions.assertEquals(16.toShort(), response.pumpRate)
        Assertions.assertEquals(8.toShort(), response.primePumpRate)
        Assertions.assertEquals(52.toShort(), response.numberOfEngagingClutchDrivePulses)
        Assertions.assertEquals(10.toShort(), response.numberOfPrimePulses)
        Assertions.assertEquals(80.toShort(), response.podExpirationTimeInHours)
        Assertions.assertEquals(4.toShort(), response.firmwareVersionMajor)
        Assertions.assertEquals(10.toShort(), response.firmwareVersionMinor)
        Assertions.assertEquals(0.toShort(), response.firmwareVersionInterim)
        Assertions.assertEquals(1.toShort(), response.bleVersionMajor)
        Assertions.assertEquals(3.toShort(), response.bleVersionMinor)
        Assertions.assertEquals(0.toShort(), response.bleVersionInterim)
        Assertions.assertEquals(4.toShort(), response.productId)
        Assertions.assertEquals(PodStatus.UID_SET, response.podStatus)
        Assertions.assertEquals(135556289L, response.lotNumber)
        Assertions.assertEquals(611540L, response.podSequenceNumber)
        Assertions.assertEquals(37879809L, response.uniqueIdReceivedInCommand)
    }
}
