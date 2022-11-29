package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import org.junit.Assert
import org.junit.jupiter.api.Test

class RFSpyTest : TestBase() {

    @Test fun testGetFirmwareVersion() {
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_1_0,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.0")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_1_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_1_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1.13")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_0,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_0,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0.1")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_2,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_2,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.16")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_2,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.17")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.0")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_2_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.17")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0.1")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_3_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1.13")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0.4")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.3")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.Version_4_x,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.5.7")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.UnknownVersion,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.UnknownVersion,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0.0")
        )
        Assert.assertEquals(
            RileyLinkFirmwareVersion.UnknownVersion,
            RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.5.5")
        )
    }
}