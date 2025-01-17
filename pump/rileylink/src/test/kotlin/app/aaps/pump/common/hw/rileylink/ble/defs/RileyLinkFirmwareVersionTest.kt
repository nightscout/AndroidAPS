package app.aaps.pump.common.hw.rileylink.ble.defs

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RileyLinkFirmwareVersionTest {

    @Test fun testIsSameVersion() {
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version1, RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_0, RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_0, RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_9, RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_x, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version1, RileyLinkFirmwareVersion.Version_1_0))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_0, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_0, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_0_9, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_1_x, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version1, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_0, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_2, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_x, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version1))

        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_0, RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_0, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_2, RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_2, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_x, RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_2_x, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2, RileyLinkFirmwareVersion.Version_2_x))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version2))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version2))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version2))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version2))

        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version3))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version3))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_3_x, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version3, RileyLinkFirmwareVersion.Version_3_x))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version3))

        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version4))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version4))
        Assertions.assertTrue(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version_4_x, RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version4, RileyLinkFirmwareVersion.Version_4_x))
    }
}