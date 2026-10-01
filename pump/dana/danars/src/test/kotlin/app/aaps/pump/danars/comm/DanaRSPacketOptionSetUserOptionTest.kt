package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRSPacketOptionSetUserOptionTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketOptionSetUserOption(aapsLogger, danaPump)
        // test params
        val params = packet.getRequestParams()
        Assertions.assertEquals((danaPump.lcdOnTimeSec and 0xff).toByte(), params[3])
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("OPTION__SET_USER_OPTION", packet.friendlyName)
    }
}