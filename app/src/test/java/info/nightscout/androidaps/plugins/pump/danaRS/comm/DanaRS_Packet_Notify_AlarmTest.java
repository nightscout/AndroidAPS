package info.nightscout.androidaps.plugins.pump.danaRS.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen Georgiev on 8/9/2018.
 */


@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class, NSUpload.class})
public class DanaRS_Packet_Notify_AlarmTest {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockNSUpload();
        DanaRS_Packet_Notify_Alarm packet = new DanaRS_Packet_Notify_Alarm();

        // test params
        assertEquals(null, packet.getRequestParams());

        // test message decoding
        // handlemessage testing fails on non-eror byte because of NSUpload not properly mocked
        packet.handleMessage(createArray(17, (byte) 0x01));
        assertEquals(false, packet.failed);
        // no error
        packet.handleMessage(createArray(17, (byte) 0));
        assertEquals(true, packet.failed);

        assertEquals("NOTIFY__ALARM", packet.getFriendlyName());
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