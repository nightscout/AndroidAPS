package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 8/28/2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, L.class})
public class MessageOriginalNamesTest {
    @Test
    public void runTest() {
        MessageOriginalNames packet = new MessageOriginalNames();
        String testName = packet.getName(0x41f2);
        assertEquals("CMD_HISTORY_ALL", testName);

    }


}