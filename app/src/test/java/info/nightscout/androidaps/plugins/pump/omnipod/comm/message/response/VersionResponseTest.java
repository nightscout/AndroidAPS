package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response;

import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class VersionResponseTest {
    @Test
    public void testRawDataShortResponse() {
        byte[] encodedData = ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced2");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        assertArrayEquals(encodedData, versionResponse.getRawData());
    }

    @Test
    public void testRawDataShortResponseWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced201");
        byte[] expected = ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced2");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        assertArrayEquals(expected, versionResponse.getRawData());
    }

    @Test
    public void testRawDataLongResponse() {
        byte[] encodedData = ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87");

        VersionResponse versionResponse = new VersionResponse(encodedData);

        assertArrayEquals(encodedData, versionResponse.getRawData());
    }

    @Test
    public void testRawDataLongResponseWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee8701");
        byte[] expected = ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87");

        VersionResponse versionResponse = new VersionResponse(encodedData);
        assertArrayEquals(expected, versionResponse.getRawData());
    }

    @Test
    public void testVersionResponse() {
        VersionResponse versionResponse = new VersionResponse(ByteUtil.fromHexString("011502070002070002020000a64000097c279c1f08ced2"));

        assertEquals(0x1f08ced2, versionResponse.getAddress());
        assertEquals(42560, versionResponse.getLot());
        assertEquals(621607, versionResponse.getTid());
        assertEquals("2.7.0", versionResponse.getPiVersion().toString());
        assertEquals("2.7.0", versionResponse.getPmVersion().toString());
    }

    @Test
    public void testLongVersionResponse() {
        VersionResponse versionResponse = new VersionResponse(ByteUtil.fromHexString("011b13881008340a5002070002070002030000a3770003ab371f00ee87"));

        assertEquals(0x1f00ee87, versionResponse.getAddress());
        assertEquals(41847, versionResponse.getLot());
        assertEquals(240439, versionResponse.getTid());
        assertEquals(PodProgressStatus.PAIRING_SUCCESS, versionResponse.getPodProgressStatus());
        assertEquals("2.7.0", versionResponse.getPiVersion().toString());
        assertEquals("2.7.0", versionResponse.getPmVersion().toString());
    }
}
