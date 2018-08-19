package info.nightscout.androidaps.plugins.PumpDanaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 31.07.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_APS_Set_Event_HistoryTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        // test for negative carbs
        DanaRS_Packet_APS_Set_Event_History historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, System.currentTimeMillis(), -1, 0);
        byte[] testparams = historyTest.getRequestParams();
        assertEquals((byte) 0, testparams[8]);
        // 5g carbs
        historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, System.currentTimeMillis(), 5, 0);
        testparams = historyTest.getRequestParams();
        assertEquals((byte) 5, testparams[8]);
        // 150g carbs
        historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, System.currentTimeMillis(), 150, 0);
        testparams = historyTest.getRequestParams();
        assertEquals((byte) 150, testparams[8]);
        // test low hard limit
        // test high hard limit

        // test message generation
        historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, System.currentTimeMillis(), 5, 0);
        testparams = historyTest.getRequestParams();
        assertEquals((byte) 5, testparams[8]);
        assertEquals(11 , testparams.length);
        assertEquals((byte)DanaRPump.CARBS, testparams[0]);

        // test message decoding
        historyTest.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, historyTest.failed);
        historyTest.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 1});
        assertEquals(true, historyTest.failed);

        assertEquals("APS_SET_EVENT_HISTORY", historyTest.getFriendlyName());
    }

}
