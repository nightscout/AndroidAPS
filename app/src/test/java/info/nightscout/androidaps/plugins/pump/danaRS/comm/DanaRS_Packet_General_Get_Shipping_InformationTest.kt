package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Get_Shipping_InformationTest : DanaRSTestBase() {

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_Shipping_Information(aapsLogger, danaRPump, dateUtil)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        // everything ok :)
        packet = DanaRS_Packet_General_Get_Shipping_Information(aapsLogger, danaRPump, dateUtil)
        packet.handleMessage(createArray(18, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("REVIEW__GET_SHIPPING_INFORMATION", packet.friendlyName)
    }
}