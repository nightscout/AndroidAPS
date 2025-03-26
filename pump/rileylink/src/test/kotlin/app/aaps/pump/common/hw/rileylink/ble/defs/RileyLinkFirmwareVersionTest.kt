package app.aaps.pump.common.hw.rileylink.ble.defs

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RileyLinkFirmwareVersionTest {

    @Test fun testIsSameVersion() {
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_1_0.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_0_0.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_0_9.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_1_x.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_0_0.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_1_0.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_0_9.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_1_x.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_2_0.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_2_2.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_2_x.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_3_x.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_4_x.isSameVersion(RileyLinkFirmwareVersion.Version1))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_2_0.isSameVersion(RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_2_0.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_2_2.isSameVersion(RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_2_2.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_2_x.isSameVersion(RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_2_x.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_3_x.isSameVersion(RileyLinkFirmwareVersion.Version2))
        Assertions.assertFalse(RileyLinkFirmwareVersionBase.Version_4_x.isSameVersion(RileyLinkFirmwareVersion.Version2))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_3_x.isSameVersion(RileyLinkFirmwareVersion.Version3))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_3_x.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_4_x.isSameVersion(RileyLinkFirmwareVersion.Version4))
        Assertions.assertTrue(RileyLinkFirmwareVersionBase.Version_4_x.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher))
    }

    @Test
    fun defaultToLowestMajorVersionTest() {
        Assertions.assertEquals(RileyLinkFirmwareVersionBase.defaultToLowestMajorVersion(1), RileyLinkFirmwareVersionBase.Version_1_x)
        Assertions.assertEquals(RileyLinkFirmwareVersionBase.defaultToLowestMajorVersion(4), RileyLinkFirmwareVersionBase.Version_4_x)
        Assertions.assertNotEquals(RileyLinkFirmwareVersionBase.defaultToLowestMajorVersion(3), RileyLinkFirmwareVersionBase.Version_4_x)
        Assertions.assertEquals(RileyLinkFirmwareVersionBase.defaultToLowestMajorVersion(5), RileyLinkFirmwareVersionBase.UnknownVersion)
    }

    @Test
    fun getFamilyMembersRecursiveTest() {
        Assertions.assertEquals(RileyLinkFirmwareVersion.Version1.familyMembers.size, 4)
        Assertions.assertEquals(RileyLinkFirmwareVersion.Version2.familyMembers.size, 3)
        Assertions.assertEquals(RileyLinkFirmwareVersion.Version3.familyMembers.size, 1)
        Assertions.assertEquals(RileyLinkFirmwareVersion.Version4.familyMembers.size, 1)
        Assertions.assertEquals(RileyLinkFirmwareVersion.Version2AndHigher.familyMembers.size, 5)
    }
}