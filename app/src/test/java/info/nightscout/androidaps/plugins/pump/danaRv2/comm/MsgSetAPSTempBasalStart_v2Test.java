package info.nightscout.androidaps.plugins.pump.danaRv2.comm;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.assertEquals;

/**
 * Created by mike on 20.11.2017.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class})
public class MsgSetAPSTempBasalStart_v2Test extends MsgSetAPSTempBasalStart_v2 {

    @Test
    public void runTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();

        // under 100% should last 30 min
        setParams(0);
        assertEquals(0, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM30MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // over 100% should last 15 min
        setParams(150);
        assertEquals(150, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM15MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // test low hard limit
        setParams(-1);
        assertEquals(0, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM30MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // test high hard limit
        setParams(550);
        assertEquals(500, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM15MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // test setting 15 min
        setParams(50, true, false);
        assertEquals(50, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM15MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // test setting 30 min
        setParams(50, false, true);
        assertEquals(50, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM30MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // over 200% set always 15 min
        setParams(250, false, true);
        assertEquals(250, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM15MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // test low hard limit
        setParams(-1, false, true);
        assertEquals(0, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM30MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();
        // test high hard limit
        setParams(550, false, true);
        assertEquals(500, intFromBuff(buffer, 0, 2));
        assertEquals(PARAM15MIN, intFromBuff(buffer, 2, 1));
        resetBuffer();

        // test message decoding
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0});
        assertEquals(true, failed);
        handleMessage(new byte[]{(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 1});
        assertEquals(false, failed);
    }

}
