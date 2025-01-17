package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlarmType
import app.aaps.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class AlarmStatusResponseTest {

    @Test fun testValidResponse() {
        val encoded = Hex.decodeHex("021602080100000501BD00000003FF01950000000000670A")
        val response = AlarmStatusResponse(encoded)

        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.ADDITIONAL_STATUS_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.ADDITIONAL_STATUS_RESPONSE.value)
        assertThat(response.statusResponseType).isEqualTo(ResponseType.StatusResponseType.ALARM_STATUS)
        assertThat(response.additionalStatusResponseType).isEqualTo(ResponseType.StatusResponseType.ALARM_STATUS.value)
        assertThat(response.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.BASAL_ACTIVE)
        assertThat(response.bolusPulsesRemaining).isEqualTo(0.toShort())
        assertThat(response.sequenceNumberOfLastProgrammingCommand).isEqualTo(5.toShort())
        assertThat(response.totalPulsesDelivered).isEqualTo(445.toShort())
        assertThat(response.alarmType).isEqualTo(AlarmType.NONE)
        assertThat(response.alarmTime).isEqualTo(0.toShort())
        assertThat(response.reservoirPulsesRemaining).isEqualTo(1023.toShort())
        assertThat(response.minutesSinceActivation).isEqualTo(405.toShort())
        assertThat(response.activeAlerts.size).isEqualTo(0)
        assertThat(response.occlusionAlarm).isFalse()
        assertThat(response.pulseInfoInvalid).isFalse()
        assertThat(response.podStatusWhenAlarmOccurred).isEqualTo(PodStatus.UNINITIALIZED)
        assertThat(response.immediateBolusWhenAlarmOccurred).isFalse()
        assertThat(response.occlusionType).isEqualTo(0x00.toByte())
        assertThat(response.occurredWhenFetchingImmediateBolusActiveInformation).isFalse()
        assertThat(response.rssi).isEqualTo(0.toShort())
        assertThat(response.receiverLowerGain).isEqualTo(0.toShort())
        assertThat(response.podStatusWhenAlarmOccurred2).isEqualTo(PodStatus.UNINITIALIZED)
        assertThat(response.returnAddressOfPodAlarmHandlerCaller).isEqualTo(26378.toShort())
    }
}
