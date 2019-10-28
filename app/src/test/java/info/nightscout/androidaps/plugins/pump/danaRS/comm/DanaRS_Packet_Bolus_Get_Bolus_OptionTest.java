package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 01.08.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Bolus_Get_Bolus_OptionTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        DanaRS_Packet_Bolus_Get_Bolus_Option packet = new DanaRS_Packet_Bolus_Get_Bolus_Option();

        // test message decoding
        DanaRPump pump = DanaRPump.getInstance();
        //if dataArray is 1 pump.isExtendedBolusEnabled should be true
        packet.handleMessage(createArray(21,(byte) 1));
        assertEquals(false, packet.failed);
        //Are options saved to pump
        assertEquals(false, !pump.isExtendedBolusEnabled);
        assertEquals(1, pump.bolusCalculationOption);
        assertEquals(1, pump.missedBolusConfig);

        packet.handleMessage(createArray(21,(byte) 0));
        assertEquals(true, packet.failed);
        //Are options saved to pump
        assertEquals(true, !pump.isExtendedBolusEnabled);

        assertEquals("BOLUS__GET_BOLUS_OPTION", packet.getFriendlyName());
    }

    byte[] createArray(int length, byte fillWith) {
        byte[] ret = new byte[length];
        for (int i = 0; i < length; i++) {
            ret[i] = fillWith;
        }
        return ret;
    }
}
