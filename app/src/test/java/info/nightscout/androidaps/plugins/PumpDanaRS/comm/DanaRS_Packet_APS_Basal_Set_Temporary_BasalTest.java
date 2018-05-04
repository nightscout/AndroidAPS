package info.nightscout.androidaps.plugins.PumpDanaRS.comm;


import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by mike on 20.11.2017.
 */

public class DanaRS_Packet_APS_Basal_Set_Temporary_BasalTest extends DanaRS_Packet_APS_Basal_Set_Temporary_Basal {

    @Test
    public void runTest() throws Exception {
        // under 100% should last 30 min
        setParams(0);
        assertEquals(0, temporaryBasalRatio);
        assertEquals(PARAM30MIN, temporaryBasalDuration);
        // over 100% should last 15 min
        setParams(150);
        assertEquals(150, temporaryBasalRatio);
        assertEquals(PARAM15MIN, temporaryBasalDuration);
        // test low hard limit
        setParams(-1);
        assertEquals(0, temporaryBasalRatio);
        assertEquals(PARAM30MIN, temporaryBasalDuration);
        // test high hard limit
        setParams(550);
        assertEquals(500, temporaryBasalRatio);
        assertEquals(PARAM15MIN, temporaryBasalDuration);
        // test setting 15 min
        setParams(50, true, false);
        assertEquals(50, temporaryBasalRatio);
        assertEquals(PARAM15MIN, temporaryBasalDuration);
        // test setting 30 min
        setParams(50, false, true);
        assertEquals(50, temporaryBasalRatio);
        assertEquals(PARAM30MIN, temporaryBasalDuration);
        // over 200% set always 15 min
        setParams(250, false, true);
        assertEquals(250, temporaryBasalRatio);
        assertEquals(PARAM15MIN, temporaryBasalDuration);
        // test low hard limit
        setParams(-1, false, true);
        assertEquals(0, temporaryBasalRatio);
        assertEquals(PARAM30MIN, temporaryBasalDuration);
        // test high hard limit
        setParams(550, false,true);
        assertEquals(500, temporaryBasalRatio);
        assertEquals(PARAM15MIN, temporaryBasalDuration);

        // test message generation
        setParams(260, true, false);
        byte[] generatedCode = getRequestParams();
        assertEquals(3 , generatedCode.length);
        assertEquals((byte)4 , generatedCode[0]);
        assertEquals((byte)1 , generatedCode[1]);
        assertEquals((byte)PARAM15MIN, generatedCode[2]);

        // test message decoding
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, failed);
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 1});
        assertEquals(true, failed);

        assertEquals("BASAL__APS_SET_TEMPORARY_BASAL", getFriendlyName());
    }

}
