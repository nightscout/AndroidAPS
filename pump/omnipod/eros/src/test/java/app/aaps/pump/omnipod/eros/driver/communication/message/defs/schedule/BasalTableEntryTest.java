package app.aaps.pump.omnipod.eros.driver.communication.message.defs.schedule;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalTableEntry;

class BasalTableEntryTest {
    @Test
    void testChecksum() {
        BasalTableEntry basalTableEntry = new BasalTableEntry(2, 300, false);
        Assertions.assertEquals(0x5a, basalTableEntry.getChecksum());
    }

    @Test
    void testChecksumWithAlternatePulses() {
        BasalTableEntry basalTableEntry = new BasalTableEntry(2, 260, true);
        Assertions.assertEquals(0x0b, basalTableEntry.getChecksum());
    }
}
