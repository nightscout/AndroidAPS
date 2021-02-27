package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test

class AlarmStatusResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("021602080100000501BD00000003FF01950000000000670A")
        val response = AlarmStatusResponse(encoded)

        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE.value, response.getMessageType())
        Assert.assertEquals(ResponseType.StatusResponseType.ALARM_STATUS, response.statusResponseType)
        Assert.assertEquals(ResponseType.StatusResponseType.ALARM_STATUS.value, response.getAdditionalStatusResponseType())
        Assert.assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.getPodStatus())
        Assert.assertEquals(DeliveryStatus.BASAL_ACTIVE, response.getDeliveryStatus())
        Assert.assertEquals(0.toShort(), response.getBolusPulsesRemaining())
        Assert.assertEquals(5.toShort(), response.getSequenceNumberOfLastProgrammingCommand())
        Assert.assertEquals(445.toShort(), response.getTotalPulsesDelivered())
        Assert.assertEquals(AlarmType.NONE, response.getAlarmType())
        Assert.assertEquals(0.toShort(), response.getAlarmTime())
        Assert.assertEquals(1023.toShort(), response.getReservoirPulsesRemaining())
        Assert.assertEquals(405.toShort(), response.getMinutesSinceActivation())
        Assert.assertFalse(response.isAlert0Active())
        Assert.assertFalse(response.isAlert1Active())
        Assert.assertFalse(response.isAlert2Active())
        Assert.assertFalse(response.isAlert3Active())
        Assert.assertFalse(response.isAlert4Active())
        Assert.assertFalse(response.isAlert5Active())
        Assert.assertFalse(response.isAlert6Active())
        Assert.assertFalse(response.isAlert7Active())
        Assert.assertFalse(response.isOcclusionAlarm())
        Assert.assertFalse(response.isPulseInfoInvalid())
        Assert.assertEquals(PodStatus.UNINITIALIZED, response.getPodStatusWhenAlarmOccurred())
        Assert.assertFalse(response.isImmediateBolusWhenAlarmOccurred())
        Assert.assertEquals(0x00.toByte(), response.getOcclusionType())
        Assert.assertFalse(response.isOccurredWhenFetchingImmediateBolusActiveInformation())
        Assert.assertEquals(0.toShort(), response.getRssi())
        Assert.assertEquals(0.toShort(), response.getReceiverLowerGain())
        Assert.assertEquals(PodStatus.UNINITIALIZED, response.getPodStatusWhenAlarmOccurred2())
        Assert.assertEquals(26378.toShort(), response.getReturnAddressOfPodAlarmHandlerCaller())
    }
}