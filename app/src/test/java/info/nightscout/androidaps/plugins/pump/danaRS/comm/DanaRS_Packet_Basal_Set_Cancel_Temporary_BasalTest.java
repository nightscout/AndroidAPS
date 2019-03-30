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
 * Created by Rumen on 01.08.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Basal_Set_Cancel_Temporary_BasalTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal packet = new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal();

        // test message decoding
        packet.handleMessage(createArray(3,(byte) 0));
        assertEquals(false, packet.failed);
        packet.handleMessage(createArray(3,(byte) 1));
        assertEquals(true, packet.failed);

        assertEquals("BASAL__CANCEL_TEMPORARY_BASAL", packet.getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith) {
        byte[] ret = new byte[length];
        for (int i = 0; i < length; i++) {
            ret[i] = fillWith;
        }
        return ret;
    }
}
