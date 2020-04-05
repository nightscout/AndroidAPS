package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Basal_Set_Cancel_Temporary_BasalTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger)
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(3, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__CANCEL_TEMPORARY_BASAL", packet.friendlyName)
    }
}