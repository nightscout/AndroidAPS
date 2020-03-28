package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Get_Shipping_VerisonTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_General_Get_Shipping_Version(aapsLogger, danaRPump)
        // test message decoding
        val ver = byteArrayOf((-78).toByte(), (-127).toByte(), (66).toByte(), (80).toByte(), (78).toByte(), (45).toByte(), (51).toByte(), (46).toByte(), (48).toByte(), (46).toByte(), (48).toByte())
        packet.handleMessage(ver)
        Assert.assertFalse(packet.failed)
        Assert.assertEquals("BPN-3.0.0", danaRPump.bleModel)
        Assert.assertEquals("GENERAL__GET_SHIPPING_VERSION", packet.friendlyName)
    }
}