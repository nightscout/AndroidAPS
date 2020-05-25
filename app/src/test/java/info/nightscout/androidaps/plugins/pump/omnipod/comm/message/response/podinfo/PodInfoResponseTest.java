package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.LogEventErrorCode;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;

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

        assertEquals(PodInfoType.FAULT_EVENT, podInfoResponse.getSubType());

        PodInfoFaultEvent podInfo = podInfoResponse.getPodInfo();
        assertFalse(podInfo.isFaultAccessingTables());
        assertEquals(LogEventErrorCode.INTERNAL_2_BIT_VARIABLE_SET_AND_MANIPULATED_IN_MAIN_LOOP_ROUTINES_2, podInfo.getLogEventErrorType());
    }

    @Test
    public void testInvalidPodInfoTypeMessageDecoding() {
        PodInfoResponse podInfoResponse = new PodInfoResponse(ByteUtil.fromHexString("0216020d0000000000ab6a038403ff03860000285708030d"));

        assertEquals(PodInfoType.FAULT_EVENT, podInfoResponse.getSubType());

        thrown.expect(ClassCastException.class);
        PodInfoActiveAlerts podInfo = podInfoResponse.getPodInfo();
    }
}
