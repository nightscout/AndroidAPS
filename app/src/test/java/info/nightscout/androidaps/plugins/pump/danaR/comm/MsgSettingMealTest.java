package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 8/30/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MsgSettingMealTest {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockBus();
        MsgSettingMeal packet = new MsgSettingMeal();

        // test message decoding
        packet.handleMessage(createArray(34, (byte) 1));
        DanaRPump pump = DanaRPump.getInstance();
        assertEquals((double) MessageBase.intFromBuff(createArray(10, (byte) 1), 0, 1) / 100d, pump.bolusStep, 0);

    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }
}