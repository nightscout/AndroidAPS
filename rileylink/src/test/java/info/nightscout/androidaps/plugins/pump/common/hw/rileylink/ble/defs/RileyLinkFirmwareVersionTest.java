package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RileyLinkFirmwareVersionTest {

    @Test
    public void testIsSameVersion() {
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version1, RileyLinkFirmwareVersion.Version1));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_0, RileyLinkFirmwareVersion.Version1));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_0, RileyLinkFirmwareVersion.Version1));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_9, RileyLinkFirmwareVersion.Version1));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_x, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version1, RileyLinkFirmwareVersion.Version_1_0));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_0, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_0, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_9, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_x, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version1, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_0, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_2, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_x, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version1));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version1));

        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version2));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version2AndHigher));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_0, RileyLinkFirmwareVersion.Version2));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_0, RileyLinkFirmwareVersion.Version2AndHigher));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_2, RileyLinkFirmwareVersion.Version2));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_2, RileyLinkFirmwareVersion.Version2AndHigher));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_x, RileyLinkFirmwareVersion.Version2));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_x, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version_2_x));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version2));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version2));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version2));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version2));

        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version3));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version2AndHigher));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version3));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version_3_x));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version3));

        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version4));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version2AndHigher));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version4));
        assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version2AndHigher));
        assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version_4_x));
    }
}