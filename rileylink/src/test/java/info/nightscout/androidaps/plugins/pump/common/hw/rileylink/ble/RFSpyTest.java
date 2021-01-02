package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;

import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
public class RFSpyTest {
    @Mock
    private AAPSLogger aapsLogger;

    @Test
    public void testGetFirmwareVersion() {
        assertEquals(RileyLinkFirmwareVersion.Version_1_0, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.0"));
        assertEquals(RileyLinkFirmwareVersion.Version_1_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1"));
        assertEquals(RileyLinkFirmwareVersion.Version_1_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 1.1.13"));

        assertEquals(RileyLinkFirmwareVersion.Version_2_0, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0"));
        assertEquals(RileyLinkFirmwareVersion.Version_2_0, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.0.1"));
        assertEquals(RileyLinkFirmwareVersion.Version_2_2, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2"));
        assertEquals(RileyLinkFirmwareVersion.Version_2_2, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.16"));
        assertEquals(RileyLinkFirmwareVersion.Version_2_2, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.2.17"));
        assertEquals(RileyLinkFirmwareVersion.Version_2_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3"));
        assertEquals(RileyLinkFirmwareVersion.Version_2_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.0"));
        assertEquals(RileyLinkFirmwareVersion.Version_2_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 2.3.17"));

        assertEquals(RileyLinkFirmwareVersion.Version_3_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0"));
        assertEquals(RileyLinkFirmwareVersion.Version_3_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.0.1"));
        assertEquals(RileyLinkFirmwareVersion.Version_3_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1"));
        assertEquals(RileyLinkFirmwareVersion.Version_3_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 3.1.13"));

        assertEquals(RileyLinkFirmwareVersion.Version_4_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0"));
        assertEquals(RileyLinkFirmwareVersion.Version_4_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.0.4"));
        assertEquals(RileyLinkFirmwareVersion.Version_4_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.3"));
        assertEquals(RileyLinkFirmwareVersion.Version_4_x, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 4.5.7"));

        assertEquals(RileyLinkFirmwareVersion.UnknownVersion, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0"));
        assertEquals(RileyLinkFirmwareVersion.UnknownVersion, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.0.0"));
        assertEquals(RileyLinkFirmwareVersion.UnknownVersion, RFSpy.getFirmwareVersion(aapsLogger, "", "subg_rfspy 5.5.5"));
    }
}