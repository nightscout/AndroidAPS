package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Basal_Get_Profile_NumberTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Get_Profile_Number(aapsLogger, danaRPump)
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        // if data.length > 4 should return fail
        Assert.assertEquals("BASAL__GET_PROFILE_NUMBER", packet.friendlyName)
    }
}