package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;
/**
 * Created by Rumen Georgiev on 30.10.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MsgStatusTempBasal_v2Test {
    @Test
    public void runTest() {
        MsgStatusTempBasal_v2 packet = new MsgStatusTempBasal_v2();
        DanaRPump pump = DanaRPump.getInstance();
        // test message decoding
        packet.handleMessage(createArray(34, (byte) 1));
        assertEquals(true, pump.isTempBasalInProgress);
        // passing an bigger number
        packet = new MsgStatusTempBasal_v2();

        packet.handleMessage(createArray(34, (byte) 2));
        assertEquals(false, pump.isTempBasalInProgress);
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

    @Before
    public void mock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
    }

}