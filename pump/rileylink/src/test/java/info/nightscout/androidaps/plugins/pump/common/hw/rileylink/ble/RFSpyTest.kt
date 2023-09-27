package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble

import app.aaps.shared.tests.TestBase
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection") class RFSpyTest : TestBase() {

    @Test fun testGetFirmwareVersion() {
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_1_0,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.0")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_1_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_1_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1.13")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_0,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_0,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0.1")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_2,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_2,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.16")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_2,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.17")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.0")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_2_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.17")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0.1")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1.13")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0.4")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.3")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.5.7")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.UnknownVersion,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.UnknownVersion,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0.0")
        )
        Assertions.assertEquals(
            RileyLinkFirmwareVersion.UnknownVersion,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.5.5")
        )
    }
}