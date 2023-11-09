package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection") class RFSpyTest : TestBase() {

    @Test fun testGetFirmwareVersion() {
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.0")).isEqualTo(RileyLinkFirmwareVersion.Version_1_0)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1")).isEqualTo(RileyLinkFirmwareVersion.Version_1_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1.13")).isEqualTo(RileyLinkFirmwareVersion.Version_1_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0")).isEqualTo(RileyLinkFirmwareVersion.Version_2_0)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0.1")).isEqualTo(RileyLinkFirmwareVersion.Version_2_0)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2")).isEqualTo(RileyLinkFirmwareVersion.Version_2_2)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.16")).isEqualTo(RileyLinkFirmwareVersion.Version_2_2)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.17")).isEqualTo(RileyLinkFirmwareVersion.Version_2_2)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3")).isEqualTo(RileyLinkFirmwareVersion.Version_2_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.0")).isEqualTo(RileyLinkFirmwareVersion.Version_2_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.17")).isEqualTo(RileyLinkFirmwareVersion.Version_2_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0")).isEqualTo(RileyLinkFirmwareVersion.Version_3_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0.1")).isEqualTo(RileyLinkFirmwareVersion.Version_3_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1")).isEqualTo(RileyLinkFirmwareVersion.Version_3_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1.13")).isEqualTo(RileyLinkFirmwareVersion.Version_3_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0")).isEqualTo(RileyLinkFirmwareVersion.Version_4_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0.4")).isEqualTo(RileyLinkFirmwareVersion.Version_4_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.3")).isEqualTo(RileyLinkFirmwareVersion.Version_4_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.5.7")).isEqualTo(RileyLinkFirmwareVersion.Version_4_x)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0")).isEqualTo(RileyLinkFirmwareVersion.UnknownVersion)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0.0")).isEqualTo(RileyLinkFirmwareVersion.UnknownVersion)
        assertThat(RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.5.5")).isEqualTo(RileyLinkFirmwareVersion.UnknownVersion)
    }
}
