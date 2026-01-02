package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlarmType
import app.aaps.pump.omnipod.dash.driver.pod.definition.NakErrorType
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class NakResponseTest {

    @Test fun testValidResponse() {
        val encoded = Hex.decodeHex("0603070009")
        val response = NakResponse(encoded)

        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.NAK_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.NAK_RESPONSE.value)
        assertThat(response.nakErrorType).isEqualTo(NakErrorType.ILLEGAL_PARAM)
        assertThat(response.alarmType).isEqualTo(AlarmType.NONE)
        assertThat(response.podStatus).isEqualTo(PodStatus.RUNNING_BELOW_MIN_VOLUME)
        assertThat(response.securityNakSyncCount).isEqualTo(0x00.toShort())
    }
}
