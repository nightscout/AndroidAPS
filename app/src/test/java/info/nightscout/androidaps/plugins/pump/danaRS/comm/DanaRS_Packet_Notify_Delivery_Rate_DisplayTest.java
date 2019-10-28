package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen Georgiev on 8/9/2018.
 */


@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class DanaRS_Packet_Notify_Delivery_Rate_DisplayTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockStrings();
        DanaRS_Packet_Notify_Delivery_Rate_Display packet = new DanaRS_Packet_Notify_Delivery_Rate_Display(1, new Treatment());

        // test params
        assertEquals(null, packet.getRequestParams());

        // test message decoding
        // 0% delivered
        packet.handleMessage(createArray(17, (byte) 0));
        assertEquals(true, packet.failed);
        // 100 % delivered
        packet.handleMessage(createArray(17, (byte) 1));
        assertEquals(false, packet.failed);

        assertEquals("NOTIFY__DELIVERY_RATE_DISPLAY", packet.getFriendlyName());
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