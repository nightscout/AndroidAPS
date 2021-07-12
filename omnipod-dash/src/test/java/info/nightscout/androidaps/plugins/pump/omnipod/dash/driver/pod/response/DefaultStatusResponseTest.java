package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

public class DefaultStatusResponseTest {
    @Test
    public void testValidResponse() throws DecoderException {
        byte[] encoded = Hex.decodeHex("1D1800A02800000463FF");
        DefaultStatusResponse response = new DefaultStatusResponse(encoded);

        assertArrayEquals(encoded, response.getEncoded());
        assertNotSame(encoded, response.getEncoded());
        assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE, response.getResponseType());
        assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE.getValue(), response.getMessageType());
        assertEquals(DeliveryStatus.BASAL_ACTIVE, response.getDeliveryStatus());
        assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.getPodStatus());
        assertEquals((short) 320, response.getTotalPulsesDelivered());
        assertEquals((short) 5, response.getSequenceNumberOfLastProgrammingCommand());
        assertEquals((short) 0, response.getBolusPulsesRemaining());
        assertFalse(response.isOcclusionAlertActive());
        assertFalse(response.isAlert1Active());
        assertFalse(response.isAlert2Active());
        assertFalse(response.isAlert3Active());
        assertFalse(response.isAlert4Active());
        assertFalse(response.isAlert5Active());
        assertFalse(response.isAlert6Active());
        assertFalse(response.isAlert7Active());
        assertEquals((short) 280, response.getMinutesSinceActivation());
        assertEquals((short) 1023, response.getReservoirPulsesRemaining());
    }
}