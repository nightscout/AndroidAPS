package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class VersionResponseTest {

    @Test fun testValidResponse() {
        val encoded = Hex.decodeHex("0115040A00010300040208146CC1000954D400FFFFFFFF")
        val response = VersionResponse(encoded)

        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.ACTIVATION_RESPONSE)
        assertThat(response.activationResponseType).isEqualTo(ResponseType.ActivationResponseType.GET_VERSION_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.ACTIVATION_RESPONSE.value)
        assertThat(response.messageLength).isEqualTo(21.toShort())
        assertThat(response.firmwareVersionMajor).isEqualTo(4.toShort())
        assertThat(response.firmwareVersionMinor).isEqualTo(10.toShort())
        assertThat(response.firmwareVersionInterim).isEqualTo(0.toShort())
        assertThat(response.bleVersionMajor).isEqualTo(1.toShort())
        assertThat(response.bleVersionMinor).isEqualTo(3.toShort())
        assertThat(response.bleVersionInterim).isEqualTo(0.toShort())
        assertThat(response.productId).isEqualTo(4.toShort())
        assertThat(response.podStatus).isEqualTo(PodStatus.FILLED)
        assertThat(response.lotNumber).isEqualTo(135556289L)
        assertThat(response.podSequenceNumber).isEqualTo(611540L)
        assertThat(response.rssi).isEqualTo(0.toByte())
        assertThat(response.receiverLowerGain).isEqualTo(0.toByte())
        assertThat(response.uniqueIdReceivedInCommand).isEqualTo(4294967295L)
    }
}
