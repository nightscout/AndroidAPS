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
 * Created by Rumen on 06.08.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Bolus_Set_Bolus_OptionTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        DanaRS_Packet_Bolus_Set_Bolus_Option packet = new DanaRS_Packet_Bolus_Set_Bolus_Option(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);

        // test params
        byte[] testparams = packet.getRequestParams();
        assertEquals(0, testparams[0]);
        assertEquals(0, testparams[18]);

        // test message decoding
        packet.handleMessage(createArray(34, (byte) 0));
//        DanaRPump testPump = DanaRPump.getInstance();
        assertEquals(false, packet.failed);

        packet.handleMessage(createArray(34, (byte) 1));
//        int valueRequested = (((byte) 1 & 0x000000FF) << 8) + (((byte) 1) & 0x000000FF);
//        assertEquals(valueRequested /100d, testPump.lastBolusAmount, 0);
        assertEquals(true, packet.failed);

        assertEquals("BOLUS__SET_BOLUS_OPTION", packet.getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }

    double[] createArray(int length, double fillWith){
        double[] ret = new double[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }
}
