package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by Rumen on 31.07.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class, DateUtil.class})
public class DanaRS_Packet_APS_Set_Event_HistoryTest {

    @Test
    public void runTest() {
        // test for negative carbs
        DanaRS_Packet_APS_Set_Event_History historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, DateUtil.now(), -1, 0);
        byte[] testparams = historyTest.getRequestParams();
        assertEquals((byte) 0, testparams[8]);
        // 5g carbs
        historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, DateUtil.now(), 5, 0);
        testparams = historyTest.getRequestParams();
        assertEquals((byte) 5, testparams[8]);
        // 150g carbs
        historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, DateUtil.now(), 150, 0);
        testparams = historyTest.getRequestParams();
        assertEquals((byte) 150, testparams[8]);
        // test low hard limit
        // test high hard limit

        // test message generation
        historyTest = new DanaRS_Packet_APS_Set_Event_History(DanaRPump.CARBS, DateUtil.now(), 5, 0);
        testparams = historyTest.getRequestParams();
        assertEquals((byte) 5, testparams[8]);
        assertEquals(11, testparams.length);
        assertEquals((byte) DanaRPump.CARBS, testparams[0]);

        // test message decoding
        historyTest.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, historyTest.failed);
        historyTest.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 1});
        assertEquals(true, historyTest.failed);

        assertEquals("APS_SET_EVENT_HISTORY", historyTest.getFriendlyName());
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        PowerMockito.mockStatic(DateUtil.class);
        when(DateUtil.now()).thenReturn(5456465445L);
    }
}
