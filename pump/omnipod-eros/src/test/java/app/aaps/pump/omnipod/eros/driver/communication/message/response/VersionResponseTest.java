package app.aaps.pump.omnipod.eros.driver.communication.message.response;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

class VersionResponseTest {
    @Test
    void testRawDataAssignAddressVersionResponse() {
        byte[] encodedData = ByteUtil.INSTANCE.fromHexString("011502070002070002020000a64000097c279c1f08ced2");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        Assertions.assertArrayEquals(encodedData, versionResponse.getRawData());
    }

    @Test
    void testRawDataAssignAddressVersionResponseWithLongerMessage() {
        byte[] encodedData = ByteUtil.INSTANCE.fromHexString("011502070002070002020000a64000097c279c1f08ced201");
        byte[] expected = ByteUtil.INSTANCE.fromHexString("011502070002070002020000a64000097c279c1f08ced2");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        Assertions.assertArrayEquals(expected, versionResponse.getRawData());
    }

    @Test
    void testRawDataSetupPodVersionResponse() {
        byte[] encodedData = ByteUtil.INSTANCE.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87");

        VersionResponse versionResponse = new VersionResponse(encodedData);

        Assertions.assertArrayEquals(encodedData, versionResponse.getRawData());
    }

    @Test
    void testRawDataSetupPodVersionResponseWithLongerMessage() {
        byte[] encodedData = ByteUtil.INSTANCE.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee8701");
        byte[] expected = ByteUtil.INSTANCE.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        Assertions.assertArrayEquals(expected, versionResponse.getRawData());
    }

    @Test
    void testAssignAddressVersionResponse() {
        VersionResponse versionResponse = new VersionResponse(ByteUtil.INSTANCE.fromHexString("011502070002070002020000a64000097c279c1f08ced2"));

        Assertions.assertTrue(versionResponse.isAssignAddressVersionResponse());
        Assertions.assertFalse(versionResponse.isSetupPodVersionResponse());
        Assertions.assertEquals(0x1f08ced2, versionResponse.getAddress());
        Assertions.assertEquals(42560, versionResponse.getLot());
        Assertions.assertEquals(621607, versionResponse.getTid());
        Assertions.assertEquals("2.7.0", versionResponse.getPiVersion().toString());
        Assertions.assertEquals("2.7.0", versionResponse.getPmVersion().toString());
        Assertions.assertNotNull(versionResponse.getRssi());
        Assertions.assertNotNull(versionResponse.getGain());
    }

    @Test
    void testSetupPodVersionResponse() {
        VersionResponse versionResponse = new VersionResponse(ByteUtil.INSTANCE.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87"));

        Assertions.assertFalse(versionResponse.isAssignAddressVersionResponse());
        Assertions.assertTrue(versionResponse.isSetupPodVersionResponse());
        Assertions.assertEquals(0x1f00ee87, versionResponse.getAddress());
        Assertions.assertEquals(41847, versionResponse.getLot());
        Assertions.assertEquals(240439, versionResponse.getTid());
        Assertions.assertEquals(PodProgressStatus.PAIRING_COMPLETED, versionResponse.getPodProgressStatus());
        Assertions.assertEquals("2.7.0", versionResponse.getPiVersion().toString());
        Assertions.assertEquals("2.7.0", versionResponse.getPmVersion().toString());
        Assertions.assertNull(versionResponse.getRssi());
        Assertions.assertNull(versionResponse.getGain());
    }
}
