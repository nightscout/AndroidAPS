package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AlarmStatusResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("021602080100000501BD00000003FF01950000000000670A")
        val response = AlarmStatusResponse(encoded)

        Assertions.assertArrayEquals(encoded, response.encoded)
        Assertions.assertNotSame(encoded, response.encoded)
        Assertions.assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE, response.responseType)
        Assertions.assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE.value, response.messageType)
        Assertions.assertEquals(ResponseType.StatusResponseType.ALARM_STATUS, response.statusResponseType)
        Assertions.assertEquals(ResponseType.StatusResponseType.ALARM_STATUS.value, response.additionalStatusResponseType)
        Assertions.assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.podStatus)
        Assertions.assertEquals(DeliveryStatus.BASAL_ACTIVE, response.deliveryStatus)
        Assertions.assertEquals(0.toShort(), response.bolusPulsesRemaining)
        Assertions.assertEquals(5.toShort(), response.sequenceNumberOfLastProgrammingCommand)
        Assertions.assertEquals(445.toShort(), response.totalPulsesDelivered)
        Assertions.assertEquals(AlarmType.NONE, response.alarmType)
        Assertions.assertEquals(0.toShort(), response.alarmTime)
        Assertions.assertEquals(1023.toShort(), response.reservoirPulsesRemaining)
        Assertions.assertEquals(405.toShort(), response.minutesSinceActivation)
        Assertions.assertEquals(0, response.activeAlerts.size)
        Assertions.assertFalse(response.occlusionAlarm)
        Assertions.assertFalse(response.pulseInfoInvalid)
        Assertions.assertEquals(PodStatus.UNINITIALIZED, response.podStatusWhenAlarmOccurred)
        Assertions.assertFalse(response.immediateBolusWhenAlarmOccurred)
        Assertions.assertEquals(0x00.toByte(), response.occlusionType)
        Assertions.assertFalse(response.occurredWhenFetchingImmediateBolusActiveInformation)
        Assertions.assertEquals(0.toShort(), response.rssi)
        Assertions.assertEquals(0.toShort(), response.receiverLowerGain)
        Assertions.assertEquals(PodStatus.UNINITIALIZED, response.podStatusWhenAlarmOccurred2)
        Assertions.assertEquals(26378.toShort(), response.returnAddressOfPodAlarmHandlerCaller)
    }
}
