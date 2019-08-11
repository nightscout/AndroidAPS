package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

import static org.junit.Assert.assertEquals;

public class PodInfoTestValuesTest {
    @Test
    public void testDecoding() {
        PodInfoTestValues podInfoTestValues = new PodInfoTestValues(ByteUtil.fromHexString("0601003FA8"));

        assertEquals((byte) 0x01, podInfoTestValues.getByte1());
        assertEquals((byte) 0x00, podInfoTestValues.getByte2());
        assertEquals((byte) 0x3f, podInfoTestValues.getByte3());
        assertEquals((byte) 0xa8, podInfoTestValues.getByte4());
    }
}
