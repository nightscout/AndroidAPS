package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

public class AlarmStatusResponseTest {
    @Test
    public void testValidResponse() throws DecoderException {
        byte[] encoded = Hex.decodeHex("021602080100000501BD00000003FF01950000000000670A");
        AlarmStatusResponse response = new AlarmStatusResponse(encoded);

        assertArrayEquals(encoded, response.getEncoded());
        assertNotSame(encoded, response.getEncoded());
        assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE, response.getResponseType());
        assertEquals(ResponseType.ADDITIONAL_STATUS_RESPONSE.getValue(), response.getMessageType());
        assertEquals(ResponseType.AdditionalStatusResponseType.ALARM_STATUS, response.getStatusResponseType());
        assertEquals(ResponseType.AdditionalStatusResponseType.ALARM_STATUS.getValue(), response.getAdditionalStatusResponseType());
        assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.getPodStatus());
        assertEquals(DeliveryStatus.BASAL_ACTIVE, response.getDeliveryStatus());
        assertEquals((short) 0, response.getBolusPulsesRemaining());
        assertEquals((short) 5, response.getSequenceNumberOfLastProgrammingCommand());
        assertEquals((short) 445, response.getTotalPulsesDelivered());
        assertEquals(AlarmType.NONE, response.getAlarmType());
        assertEquals((short) 0, response.getAlarmTime());
        assertEquals((short) 1023, response.getReservoirPulsesRemaining());
        assertEquals((short) 405, response.getMinutesSinceActivation());
        assertFalse(response.isAlert0Active());
        assertFalse(response.isAlert1Active());
        assertFalse(response.isAlert2Active());
        assertFalse(response.isAlert3Active());
        assertFalse(response.isAlert4Active());
        assertFalse(response.isAlert5Active());
        assertFalse(response.isAlert6Active());
        assertFalse(response.isAlert7Active());
        assertFalse(response.isOcclusionAlarm());
        assertFalse(response.isPulseInfoInvalid());
        assertEquals(PodStatus.UNINITIALIZED, response.getPodStatusWhenAlarmOccurred());
        assertFalse(response.isImmediateBolusWhenAlarmOccurred());
        assertEquals((byte) 0x00, response.getOcclusionType());
        assertFalse(response.isOccurredWhenFetchingImmediateBolusActiveInformation());
        assertEquals(0, response.getRssi());
        assertEquals(0, response.getReceiverLowerGain());
        assertEquals(PodStatus.UNINITIALIZED, response.getPodStatusWhenAlarmOccurred2());
        assertEquals((short) 26378, response.getReturnAddressOfPodAlarmHandlerCaller());
    }
}