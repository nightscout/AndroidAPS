package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Rumen Georgiev on 8/31/2018.
 */
public class RecordTypesTest {
    @Test
    public void runTest() {
        RecordTypes packet = new RecordTypes();
        assertEquals((byte) 0x01, packet.RECORD_TYPE_BOLUS);
    }
}
