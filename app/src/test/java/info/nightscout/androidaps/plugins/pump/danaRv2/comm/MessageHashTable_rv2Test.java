package info.nightscout.androidaps.plugins.pump.danaRv2.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;
/**
 * Created by Rumen Georgiev on 30.10.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MessageHashTable_rv2Test {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        MessageHashTableRv2 hashTableRv2 = MessageHashTableRv2.INSTANCE;

        MessageBase forTesting = new MsgStatusAPS_v2();
        MessageBase testPacket = MessageHashTableRv2.INSTANCE.findMessage(forTesting.getCommand());
        assertEquals(0xE001, testPacket.getCommand());
        // try putting another command
        MessageBase testMessage = new MessageBase();
        testMessage.SetCommand(0xE005);
        hashTableRv2.put(testMessage);
        assertEquals(0xE005, hashTableRv2.findMessage(0xE005).getCommand());
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