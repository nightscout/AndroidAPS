package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import com.cozmo.danar.util.BleCommandUtil;

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
 * Created by Rumen Georgiev on 8/9/2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class, DanaRSMessageHashTable.class})
public class DanaRSMessageHashTableTest {
    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
//        HashMap<Integer, DanaRS_Packet> messages = new DanaRSMessageHashTable().messages;
        DanaRS_Packet forTesting = new DanaRS_Packet_APS_Set_Event_History();
        DanaRS_Packet testPacket = DanaRSMessageHashTable.findMessage(forTesting.getCommand());
        assertEquals(BleCommandUtil.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY, testPacket.getOpCode());
    }

}
