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
 * Created by Rumen on 08.08.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_General_Get_Pump_CheckTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        DanaRS_Packet_General_Get_Pump_Check packet = new DanaRS_Packet_General_Get_Pump_Check();

        // test params
        byte[] testparams = packet.getRequestParams();
        assertEquals(null, packet.getRequestParams());

        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, (byte) 0));
        assertEquals(true, packet.failed);
        // everything ok :)
        packet = new DanaRS_Packet_General_Get_Pump_Check();
        packet.handleMessage(createArray(15, (byte) 0));
        assertEquals(false, packet.failed);

//        packet.handleMessage(createArray(15, (byte) 161));
//        assertEquals(true, packet.failed);

        assertEquals("REVIEW__GET_PUMP_CHECK", packet.getFriendlyName());
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