package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.pump.core.utils.ByteUtil;

public class VersionResponseTest {
    @Test
    public void testRawDataAssignAddressVersionResponse() {
        byte[] encodedData = ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced2");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        assertArrayEquals(encodedData, versionResponse.getRawData());
    }

    @Test
    public void testRawDataAssignAddressVersionResponseWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced201");
        byte[] expected = ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced2");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        assertArrayEquals(expected, versionResponse.getRawData());
    }

    @Test
    public void testRawDataSetupPodVersionResponse() {
        byte[] encodedData = ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87");

        VersionResponse versionResponse = new VersionResponse(encodedData);

        assertArrayEquals(encodedData, versionResponse.getRawData());
    }

    @Test
    public void testRawDataSetupPodVersionResponseWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee8701");
        byte[] expected = ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        assertArrayEquals(expected, versionResponse.getRawData());
    }

    @Test
    public void testAssignAddressVersionResponse() {
        VersionResponse versionResponse = new VersionResponse(ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced2"));

        assertTrue(versionResponse.isAssignAddressVersionResponse());
        assertFalse(versionResponse.isSetupPodVersionResponse());
        assertEquals(0x1f08ced2, versionResponse.getAddress());
        assertEquals(42560, versionResponse.getLot());
        assertEquals(621607, versionResponse.getTid());
        assertEquals("2.7.0", versionResponse.getPiVersion().toString());
        assertEquals("2.7.0", versionResponse.getPmVersion().toString());
        assertNotNull(versionResponse.getRssi());
        assertNotNull(versionResponse.getGain());
    }

    @Test
    public void testSetupPodVersionResponse() {
        VersionResponse versionResponse = new VersionResponse(ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87"));

        assertFalse(versionResponse.isAssignAddressVersionResponse());
        assertTrue(versionResponse.isSetupPodVersionResponse());
        assertEquals(0x1f00ee87, versionResponse.getAddress());
        assertEquals(41847, versionResponse.getLot());
        assertEquals(240439, versionResponse.getTid());
        assertEquals(PodProgressStatus.PAIRING_COMPLETED, versionResponse.getPodProgressStatus());
        assertEquals("2.7.0", versionResponse.getPiVersion().toString());
        assertEquals("2.7.0", versionResponse.getPmVersion().toString());
        assertNull(versionResponse.getRssi());
        assertNull(versionResponse.getGain());
    }
}
