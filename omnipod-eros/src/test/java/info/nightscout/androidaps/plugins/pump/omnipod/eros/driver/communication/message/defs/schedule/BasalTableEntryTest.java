package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.defs.schedule;

import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule.BasalTableEntry;

import static junit.framework.Assert.assertEquals;

public class BasalTableEntryTest {
    @Test
    public void testChecksum() {
        BasalTableEntry basalTableEntry = new BasalTableEntry(2, 300, false);
        assertEquals(0x5a, basalTableEntry.getChecksum());
    }

    @Test
    public void testChecksumWithAlternatePulses() {
        BasalTableEntry basalTableEntry = new BasalTableEntry(2, 260, true);
        assertEquals(0x0b, basalTableEntry.getChecksum());
    }
}
