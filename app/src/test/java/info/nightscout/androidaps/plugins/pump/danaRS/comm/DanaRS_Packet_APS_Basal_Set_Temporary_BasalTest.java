package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by mike on 20.11.2017.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_APS_Basal_Set_Temporary_BasalTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        // under 100% should last 30 min
        DanaRS_Packet_APS_Basal_Set_Temporary_Basal packet = new DanaRS_Packet_APS_Basal_Set_Temporary_Basal();
        packet.setParams(0);
        assertEquals(0, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM30MIN, packet.temporaryBasalDuration);
        //constructor with param
        packet = new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(10);
        assertEquals(10, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM30MIN, packet.temporaryBasalDuration);
        packet = new DanaRS_Packet_APS_Basal_Set_Temporary_Basal(20, true, false);
        assertEquals(20, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM15MIN, packet.temporaryBasalDuration);
        // over 100% should last 15 min
        packet.setParams(150);
        assertEquals(150, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM15MIN, packet.temporaryBasalDuration);
        // test low hard limit
        packet.setParams(-1);
        assertEquals(0, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM30MIN, packet.temporaryBasalDuration);
        // test high hard limit
        packet.setParams(550);
        assertEquals(500, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM15MIN, packet.temporaryBasalDuration);
        // test setting 15 min
        packet.setParams(50, true, false);
        assertEquals(50, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM15MIN, packet.temporaryBasalDuration);
        // test setting 30 min
        packet.setParams(50, false, true);
        assertEquals(50, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM30MIN, packet.temporaryBasalDuration);
        // over 200% set always 15 min
        packet.setParams(250, false, true);
        assertEquals(250, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM15MIN, packet.temporaryBasalDuration);
        // test low hard limit
        packet.setParams(-1, false, true);
        assertEquals(0, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM30MIN, packet.temporaryBasalDuration);
        // test high hard limit
        packet.setParams(550, false, true);
        assertEquals(500, packet.temporaryBasalRatio);
        assertEquals(packet.PARAM15MIN, packet.temporaryBasalDuration);

        // test message generation
        packet.setParams(260, true, false);
        byte[] generatedCode = packet.getRequestParams();
        assertEquals(3, generatedCode.length);
        assertEquals((byte) 4, generatedCode[0]);
        assertEquals((byte) 1, generatedCode[1]);
        assertEquals((byte) packet.PARAM15MIN, generatedCode[2]);

        // test message decoding
        packet.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, packet.failed);
        packet.handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 1});
        assertEquals(true, packet.failed);

        assertEquals("BASAL__APS_SET_TEMPORARY_BASAL", packet.getFriendlyName());
    }

}
