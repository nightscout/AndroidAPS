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
 * Created by Rumen on 31.07.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Basal_Get_Profile_Basal_RateTest extends DanaRS_Packet_Basal_Get_Profile_Basal_Rate {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        // test if pumpRofile array is set right
        double basal01 = byteArrayToInt(getBytes(createArray(50, (byte) 1), 2, 2)) / 100d;
        double basal05 = byteArrayToInt(getBytes(createArray(50, (byte) 5), 2, 2)) / 100d;
        double basal12 = byteArrayToInt(getBytes(createArray(50, (byte) 12), 2, 2)) / 100d;
        // basal rate > 1U/hr
        double basal120 = byteArrayToInt(getBytes(createArray(50, (byte) 120), 2, 2)) / 100d;
        DanaRS_Packet_Basal_Get_Profile_Basal_Rate testPacket = new DanaRS_Packet_Basal_Get_Profile_Basal_Rate(1);
        byte[] params = testPacket.getRequestParams();
        assertEquals((byte) 1, params[0]);
        testPacket.handleMessage(createArray(50, (byte) 0));
        assertEquals(0.0d,  testPacket.pump.pumpProfiles[1][1],0);
        testPacket.handleMessage(createArray(50, (byte) 1));
        assertEquals(basal01,  testPacket.pump.pumpProfiles[1][2],0);
        testPacket.handleMessage(createArray(50, (byte) 5));
        assertEquals(basal05,  testPacket.pump.pumpProfiles[1][1],0);
        testPacket.handleMessage(createArray(50, (byte) 12));
        assertEquals(basal12,  testPacket.pump.pumpProfiles[1][1],0);
        testPacket.handleMessage(createArray(50, (byte) 120));
        assertEquals(basal120,  testPacket.pump.pumpProfiles[1][1],0);

        assertEquals("BASAL__GET_PROFILE_BASAL_RATE", getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }

}
