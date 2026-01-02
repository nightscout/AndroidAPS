package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketGeneralGetPumpCheckTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        var packet = DanaRSPacketGeneralGetPumpCheck(aapsLogger, rh, danaPump, uiInteraction)
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        packet = DanaRSPacketGeneralGetPumpCheck(aapsLogger, rh, danaPump, uiInteraction)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals("REVIEW__GET_PUMP_CHECK", packet.friendlyName)
    }
}