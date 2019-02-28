package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 8/28/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class, DanaRPlugin.class})
public class MsgCheckValueTest {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockBus();
        Treatment t = new Treatment();
        MsgCheckValue packet = new MsgCheckValue();

        // test message decoding
        packet.handleMessage(createArray(34, (byte) 3));
        DanaRPump pump = DanaRPump.getInstance();
        assertEquals(DanaRPump.EXPORT_MODEL, pump.model);
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