package info.nightscout.androidaps.plugins.pump.danaRv2.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.treatments.TreatmentService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 30.10.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class, TreatmentsPlugin.class, TreatmentService.class})
public class MsgHistoryEvents_v2Test {
    @Test
    public void runTest() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockBus();
        AAPSMocker.mockDatabaseHelper();
        AAPSMocker.mockTreatmentPlugin();
        AAPSMocker.mockTreatmentService();
        MsgHistoryEvents_v2 packet = new MsgHistoryEvents_v2();

        // test message decoding
        //last message in history
        packet.handleMessage(createArray(34, (byte) 0xFF));
        assertEquals(true, packet.done);
        // passing an bigger number
        packet = new MsgHistoryEvents_v2();
        packet.handleMessage(createArray(34, (byte) 17));
        assertEquals(false, packet.done);

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