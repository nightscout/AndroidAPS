package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 02.08.2018.
 */

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("info.nightscout.androidaps.logging.L")
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Basal_Set_Temporary_BasalTest extends DanaRS_Packet_Basal_Set_Temporary_Basal {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();

        DanaRS_Packet_Basal_Set_Temporary_Basal testPacket = new DanaRS_Packet_Basal_Set_Temporary_Basal(50, 20);
        // params
        byte[] params = testPacket.getRequestParams();
        // is ratio 50
        assertEquals(50, params[0]);
        // is duration 20
        assertEquals(20, params[1]);

        // test message decoding
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0});
        assertEquals(false, failed);
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 1});
        assertEquals(true, failed);

        assertEquals("BASAL__SET_TEMPORARY_BASAL", getFriendlyName());
    }

}
