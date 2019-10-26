package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen Georgiev on 8/30/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class, ConfigBuilderPlugin.class})
public class MsgInitConnStatusTimeTest {
    @Test
    public void runTest() {
        MsgInitConnStatusTime packet = new MsgInitConnStatusTime();

        // test message decoding
        packet.handleMessage(createArray(20, (byte) 1));
        // message smaller than 10
        assertEquals(false, packet.failed);
        packet.handleMessage(createArray(15, (byte) 1));
        assertEquals(true, packet.failed);

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
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockCommandQueue();
    }
}