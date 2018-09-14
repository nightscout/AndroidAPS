package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.NSClientInternal.NSUpload;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.utils.SP;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 8/30/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MsgStatusBolusExtendedTest {
    @Mock
    Context context;
    @Test
    public void runTest() throws Exception{
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        try {
            AAPSMocker.mockTreatmentService();
        } catch (Exception e){

        }
        MsgStatusBolusExtended packet = new MsgStatusBolusExtended();
        // test message decoding
        //TODO Find a way to mock treatments plugin
//        packet.handleMessage(createArray(34, (byte) 7));
        DanaRPump pump = DanaRPump.getInstance();
//        assertEquals((double) MessageBase.intFromBuff(createArray(10, (byte) 7), 2, 2)/100d, pump.extendedBolusAmount,0);

    }

    byte[] createArray(int length, byte fillWith){
        byte[] ret = new byte[length];
        for(int i = 0; i<length; i++){
            ret[i] = fillWith;
        }
        return ret;
    }
}