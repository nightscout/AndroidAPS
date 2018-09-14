package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.utils.SP;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 8/30/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MsgInitConnStatusBolusTest {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockBus();
        MsgInitConnStatusBolus packet = new MsgInitConnStatusBolus();

        // test message decoding
        packet.handleMessage(createArray(34, (byte) 1));
        // message bigger than 22
        assertEquals(true, packet.failed);
        packet.handleMessage(createArray(20, (byte) 1));
        DanaRPump pump = DanaRPump.getInstance();
        assertEquals(true, pump.isExtendedBolusEnabled);

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