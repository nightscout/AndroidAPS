package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_General_Get_PasswordTest : DanaRSTestBase() {

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_Password(aapsLogger, danaRPump)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRS_Packet_General_Get_Password(aapsLogger, danaRPump)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("REVIEW__GET_PASSWORD", packet.friendlyName)
    }
}