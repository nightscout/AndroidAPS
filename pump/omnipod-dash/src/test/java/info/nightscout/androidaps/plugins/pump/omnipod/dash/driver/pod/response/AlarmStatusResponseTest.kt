package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class AlarmStatusResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("021602080100000501BD00000003FF01950000000000670A")
        val response = AlarmStatusResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE.value, response.messageType)
        Assert.assertEquals(ResponseType.StatusResponseType.ALARM_STATUS, response.statusResponseType)
        Assert.assertEquals(ResponseType.StatusResponseType.ALARM_STATUS.value, response.additionalStatusResponseType)
        Assert.assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.podStatus)
        Assert.assertEquals(DeliveryStatus.BASAL_ACTIVE, response.deliveryStatus)
        Assert.assertEquals(0.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(5.toShort(), response.sequenceNumberOfLastProgrammingCommand)
        Assert.assertEquals(445.toShort(), response.totalPulsesDelivered)
        Assert.assertEquals(AlarmType.NONE, response.alarmType)
        Assert.assertEquals(0.toShort(), response.alarmTime)
        Assert.assertEquals(1023.toShort(), response.reservoirPulsesRemaining)
        Assert.assertEquals(405.toShort(), response.minutesSinceActivation)
        Assert.assertEquals(0, response.activeAlerts.size)
        Assert.assertFalse(response.occlusionAlarm)
        Assert.assertFalse(response.pulseInfoInvalid)
        Assert.assertEquals(PodStatus.UNINITIALIZED, response.podStatusWhenAlarmOccurred)
        Assert.assertFalse(response.immediateBolusWhenAlarmOccurred)
        Assert.assertEquals(0x00.toByte(), response.occlusionType)
        Assert.assertFalse(response.occurredWhenFetchingImmediateBolusActiveInformation)
        Assert.assertEquals(0.toShort(), response.rssi)
        Assert.assertEquals(0.toShort(), response.receiverLowerGain)
        Assert.assertEquals(PodStatus.UNINITIALIZED, response.podStatusWhenAlarmOccurred2)
        Assert.assertEquals(26378.toShort(), response.returnAddressOfPodAlarmHandlerCaller)
    }
}
