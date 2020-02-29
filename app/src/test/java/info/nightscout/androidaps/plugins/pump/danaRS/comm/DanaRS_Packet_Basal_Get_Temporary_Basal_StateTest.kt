package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Basal_Get_Temporary_Basal_StateTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump)
        // test message decoding
        packet.handleMessage(createArray(50, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(50, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__TEMPORARY_BASAL_STATE", packet.friendlyName)
    }
}