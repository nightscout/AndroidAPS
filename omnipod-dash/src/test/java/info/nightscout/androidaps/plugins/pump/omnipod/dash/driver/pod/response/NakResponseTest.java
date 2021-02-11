package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.NakErrorType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class NakResponseTest {
    @Test
    public void testValidResponse() throws DecoderException {
        byte[] encoded = Hex.decodeHex("0603070009");

        NakResponse response = new NakResponse(encoded);
        assertArrayEquals(encoded, response.getEncoded());
        assertNotSame(encoded, response.getEncoded());
        assertEquals(ResponseType.NAK_RESPONSE, response.getResponseType());
        assertEquals(ResponseType.NAK_RESPONSE.getValue(), response.getMessageType());
        assertEquals(NakErrorType.ILLEGAL_PARAM, response.getNakErrorType());
        assertEquals(AlarmType.NONE, response.getAlarmType());
        assertEquals(PodStatus.RUNNING_BELOW_MIN_VOLUME, response.getPodStatus());
        assertEquals((byte) 0x00, response.getSecurityNakSyncCount());
    }
}