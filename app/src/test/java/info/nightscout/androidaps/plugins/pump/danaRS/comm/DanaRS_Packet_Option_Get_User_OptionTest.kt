package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Option_Get_User_OptionTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Option_Get_User_Option(aapsLogger, danaRPump)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(20, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(20, 5.toByte()))
        Assert.assertEquals(5, danaRPump.lcdOnTimeSec)
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("OPTION__GET_USER_OPTION", packet.friendlyName)
    }
}