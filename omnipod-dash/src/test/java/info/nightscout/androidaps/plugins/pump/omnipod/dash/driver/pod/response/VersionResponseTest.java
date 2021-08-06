package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class VersionResponseTest {

    @Test
    public void testValidResponse() throws DecoderException {
        byte[] encoded = Hex.decodeHex("0115040A00010300040208146CC1000954D400FFFFFFFF");
        VersionResponse response = new VersionResponse(encoded);

        assertArrayEquals(encoded, response.getEncoded());
        assertNotSame(encoded, response.getEncoded());
        assertEquals(ResponseType.ACTIVATION_RESPONSE, response.getResponseType());
        assertEquals(ResponseType.ActivationResponseType.GET_VERSION_RESPONSE, response.getActivationResponseType());

        assertEquals(ResponseType.ACTIVATION_RESPONSE.getValue(), response.getMessageType());
        assertEquals(21, response.getMessageLength());
        assertEquals(4, response.getFirmwareVersionMajor());
        assertEquals(10, response.getFirmwareVersionMinor());
        assertEquals(0, response.getFirmwareVersionInterim());
        assertEquals(1, response.getBleVersionMajor());
        assertEquals(3, response.getBleVersionMinor());
        assertEquals(0, response.getBleVersionInterim());
        assertEquals(4, response.getProductId());
        assertEquals(PodStatus.FILLED, response.getPodStatus());
        assertEquals(135556289, response.getLotNumber());
        assertEquals(611540, response.getPodSequenceNumber());
        assertEquals(0L, response.getRssi());
        assertEquals(0L, response.getReceiverLowerGain());
        assertEquals(4294967295L, response.getUniqueIdReceivedInCommand());
    }
}