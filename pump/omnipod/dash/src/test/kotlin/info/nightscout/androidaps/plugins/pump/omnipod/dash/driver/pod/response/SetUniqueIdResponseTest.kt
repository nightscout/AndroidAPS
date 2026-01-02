package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class SetUniqueIdResponseTest {

    @Test fun testValidResponse() {
        val encoded = Hex.decodeHex("011B13881008340A50040A00010300040308146CC1000954D402420001")
        val response = SetUniqueIdResponse(encoded)

        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.ACTIVATION_RESPONSE)
        assertThat(response.activationResponseType).isEqualTo(ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.ACTIVATION_RESPONSE.value)
        assertThat(response.messageLength).isEqualTo(27.toShort())
        assertThat(response.pulseVolumeInTenThousandthMicroLiter).isEqualTo(5000.toShort())
        assertThat(response.pumpRate).isEqualTo(16.toShort())
        assertThat(response.primePumpRate).isEqualTo(8.toShort())
        assertThat(response.numberOfEngagingClutchDrivePulses).isEqualTo(52.toShort())
        assertThat(response.numberOfPrimePulses).isEqualTo(10.toShort())
        assertThat(response.podExpirationTimeInHours).isEqualTo(80.toShort())
        assertThat(response.firmwareVersionMajor).isEqualTo(4.toShort())
        assertThat(response.firmwareVersionMinor).isEqualTo(10.toShort())
        assertThat(response.firmwareVersionInterim).isEqualTo(0.toShort())
        assertThat(response.bleVersionMajor).isEqualTo(1.toShort())
        assertThat(response.bleVersionMinor).isEqualTo(3.toShort())
        assertThat(response.bleVersionInterim).isEqualTo(0.toShort())
        assertThat(response.productId).isEqualTo(4.toShort())
        assertThat(response.podStatus).isEqualTo(PodStatus.UID_SET)
        assertThat(response.lotNumber).isEqualTo(135556289L)
        assertThat(response.podSequenceNumber).isEqualTo(611540L)
        assertThat(response.uniqueIdReceivedInCommand).isEqualTo(37879809L)
    }
}
