package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_General_Get_More_InformationTest : DanaRSTestBase() {

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_More_Information(aapsLogger, danaRPump, dateUtil)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(13, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRS_Packet_General_Get_More_Information(aapsLogger, danaRPump, dateUtil)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(15, 161.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("REVIEW__GET_MORE_INFORMATION", packet.friendlyName)
    }
}