package app.aaps.pump.omnipod.common.bledriver.pod.response

import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
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

    @Test fun testPdmRefUsesAlarmTimeWhenValid() {
        val response = createResponseWithTimes(alarmTime = 180, minutesSinceActivation = 900)

        assertThat(response.pdmRef).isEqualTo("19-00003-02251-062")
    }

    @Test fun testPdmRefFallsBackWhenAlarmTimeIsZero() {
        val response = createResponseWithTimes(alarmTime = 0, minutesSinceActivation = 900)

        assertThat(response.pdmRef).isEqualTo("19-00015-02251-062")
    }

    @Test fun testPdmRefFallsBackWhenAlarmTimeIsFFFF() {
        val response = createResponseWithTimes(alarmTime = 0xffff, minutesSinceActivation = 900)

        assertThat(response.pdmRef).isEqualTo("19-00015-02251-062")
    }

    @Test fun testPdmRefIsNullWhenThereIsNoFault() {
        val response = createResponse(AlarmType.NONE.value.toInt() and 0xff)

        assertThat(response.alarmType).isEqualTo(AlarmType.NONE)
        assertThat(response.pdmRef).isNull()
    }

    @Test fun testPdmRefKeepsTheRawByteForAnUnrecognizedFaultCode() {
        // 0xb0 falls in a gap between ALARM_BLE_REQ_STUCK_HIGH (0xaf) and ALARM_BLE_STATE_MACHINE_1 (0xb1):
        // byValue() collapses it to UNKNOWN, but the Ref code must still show the real byte, not 255.
        val response = createResponse(0xb0)

        assertThat(response.alarmType).isEqualTo(AlarmType.UNKNOWN)
        assertThat(response.pdmRef).isEqualTo("19-00003-02251-176")
    }

    @Test fun testPdmRefUsesReservoirEmptyRefType() {
        val response = createResponse(AlarmType.ALARM_EMPTY_RESERVOIR.value.toInt() and 0xff)

        assertThat(response.pdmRef).isEqualTo("14-00003-02251-024")
    }

    @Test fun testPdmRefUsesAutoOffRefType() {
        val response = createResponse(AlarmType.ALARM_ALERT0.value.toInt() and 0xff)

        assertThat(response.pdmRef).isEqualTo("15-00003-02251-041")
    }

    @Test fun testPdmRefUsesPodExpiredRefType() {
        val response = createResponse(AlarmType.ALARM_PUMP_EXPIRED.value.toInt() and 0xff)

        assertThat(response.pdmRef).isEqualTo("16-00003-02251-028")
    }

    @Test fun testPdmRefUsesOccludedRefType() {
        val response = createResponse(AlarmType.ALARM_OCCLUDED.value.toInt() and 0xff)

        assertThat(response.pdmRef).isEqualTo("17-00003-02251-020")
    }

    private fun createResponseWithTimes(alarmTime: Int, minutesSinceActivation: Int): AlarmStatusResponse =
        createResponse(AlarmType.ALARM_FLASH_FAILURE.value.toInt() and 0xff, alarmTime, minutesSinceActivation)

    private fun createResponse(alarmTypeByteValue: Int, alarmTime: Int = 180, minutesSinceActivation: Int = 900): AlarmStatusResponse {
        val encoded = Hex.decodeHex("021602080100000501BD00000003FF01950000000000670A")
        encoded[10] = alarmTypeByteValue.toByte()
        encoded[11] = (alarmTime shr 8).toByte()
        encoded[12] = alarmTime.toByte()
        encoded[15] = (minutesSinceActivation shr 8).toByte()
        encoded[16] = minutesSinceActivation.toByte()
        return AlarmStatusResponse(encoded)
    }
}
