package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class SetUniqueIdResponseTest {
    @Test
    public void testValidResponse() throws DecoderException {
        byte[] encoded = Hex.decodeHex("011B13881008340A50040A00010300040308146CC1000954D402420001");
        SetUniqueIdResponse response = new SetUniqueIdResponse(encoded);

        assertArrayEquals(encoded, response.getEncoded());
        assertNotSame(encoded, response.getEncoded());
        assertEquals(ResponseType.ACTIVATION_RESPONSE, response.getResponseType());
        assertEquals(ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE, response.getActivationResponseType());

        assertEquals(ResponseType.ACTIVATION_RESPONSE.getValue(), response.getMessageType());
        assertEquals(27, response.getMessageLength());
        assertEquals(5000, response.getPulseVolumeInTenThousandthMicroLiter());
        assertEquals(16, response.getDeliveryRate());
        assertEquals(8, response.getPrimeRate());
        assertEquals(52, response.getNumberOfEngagingClutchDrivePulses());
        assertEquals(10, response.getNumberOfPrimePulses());
        assertEquals(80, response.getPodExpirationTimeInHours());
        assertEquals(4, response.getFirmwareVersionMajor());
        assertEquals(10, response.getFirmwareVersionMinor());
        assertEquals(0, response.getFirmwareVersionInterim());
        assertEquals(1, response.getBleVersionMajor());
        assertEquals(3, response.getBleVersionMinor());
        assertEquals(0, response.getBleVersionInterim());
        assertEquals(4, response.getProductId());
        assertEquals(PodStatus.UID_SET, response.getPodStatus());
        assertEquals(135556289L, response.getLotNumber());
        assertEquals(611540L, response.getPodSequenceNumber());
        assertEquals(37879809L, response.getUniqueIdReceivedInCommand());
    }
}