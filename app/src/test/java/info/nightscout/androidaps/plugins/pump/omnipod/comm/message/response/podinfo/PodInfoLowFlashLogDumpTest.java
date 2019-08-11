package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

import static org.junit.Assert.assertEquals;

public class PodInfoLowFlashLogDumpTest {
    @Test
    public void testDecoding() {
        PodInfoLowFlashLogDump podInfoLowFlashLogDump = new PodInfoLowFlashLogDump(ByteUtil.fromHexString("4600791f00ee841f00ee84ff00ff00ffffffffffff0000ffffffffffffffffffffffff04060d10070000a62b0004e3db0000ffffffffffffff32cd50af0ff014eb01fe01fe06f9ff00ff0002fd649b14eb14eb07f83cc332cd05fa02fd58a700ffffffffffffffffffffffffffffffffffffffffffffffffffffffff")); // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        assertEquals(121, podInfoLowFlashLogDump.getNumberOfBytes());
        assertEquals(0x1f00ee84, podInfoLowFlashLogDump.getPodAddress());
    }
}
