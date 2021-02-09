package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PodInfoResponseTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testRawData() {
        byte[] encodedData = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d");

        PodInfoResponse podInfoResponse = new PodInfoResponse(encodedData);

        assertArrayEquals(encodedData, podInfoResponse.getRawData());
    }

    @Test
    public void testRawDataWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d01");
        byte[] expected = ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d");

        PodInfoResponse podInfoResponse = new PodInfoResponse(encodedData);

        assertArrayEquals(expected, podInfoResponse.getRawData());
    }

    @Test
    public void testMessageDecoding() {
        PodInfoResponse podInfoResponse = new PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d"));

        assertEquals(PodInfoType.DETAILED_STATUS, podInfoResponse.getSubType());

        PodInfoDetailedStatus podInfo = (PodInfoDetailedStatus) podInfoResponse.getPodInfo();
        assertFalse(podInfo.isFaultAccessingTables());
        assertEquals(0x01, podInfo.getErrorEventInfo().getInternalVariable());
    }

    @Test
    public void testInvalidPodInfoTypeMessageDecoding() {
        PodInfoResponse podInfoResponse = new PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d"));

        assertEquals(PodInfoType.DETAILED_STATUS, podInfoResponse.getSubType());

        thrown.expect(ClassCastException.class);
        PodInfoActiveAlerts podInfo = (PodInfoActiveAlerts) podInfoResponse.getPodInfo();
    }
}
