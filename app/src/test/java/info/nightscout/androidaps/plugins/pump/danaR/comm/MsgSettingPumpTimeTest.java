package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Date;

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
public class MsgSettingPumpTimeTest {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        MsgSettingPumpTime packet = new MsgSettingPumpTime();
        DanaRPump pump = DanaRPump.getInstance();
        pump.units = DanaRPump.UNITS_MGDL;
        // test message decoding
        byte[] bytes = createArray(34, (byte) 7);
        long time =
                new Date(
                        100 + MessageBase.intFromBuff(bytes, 5, 1),
                        MessageBase.intFromBuff(bytes, 4, 1) - 1,
                        MessageBase.intFromBuff(bytes, 3, 1),
                        MessageBase.intFromBuff(bytes, 2, 1),
                        MessageBase.intFromBuff(bytes, 1, 1),
                        MessageBase.intFromBuff(bytes, 0, 1)
                ).getTime();
        packet.handleMessage(bytes);
        pump = DanaRPump.getInstance();
        assertEquals(time, pump.pumpTime);

    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }
}