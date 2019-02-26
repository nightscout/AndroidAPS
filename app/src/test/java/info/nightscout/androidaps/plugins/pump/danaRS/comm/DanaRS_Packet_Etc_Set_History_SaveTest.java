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
public class DanaRS_Packet_Etc_Set_History_SaveTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        DanaRS_Packet_Etc_Set_History_Save packet = new DanaRS_Packet_Etc_Set_History_Save(0,0,0,0,0,0,0,0,2);

        // test params
        byte[] testparams = packet.getRequestParams();
        assertEquals(2, testparams[8]);
        assertEquals((byte) (2 >>> 8), testparams[9]);

        // test message decoding
        packet.handleMessage(createArray(34, (byte) 0));
        assertEquals(false, packet.failed);

        packet.handleMessage(createArray(34, (byte) 1));
        assertEquals(true, packet.failed);

        assertEquals("ETC__SET_HISTORY_SAVE", packet.getFriendlyName());
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