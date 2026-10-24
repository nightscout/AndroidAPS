package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketGeneralGetShippingInformationTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        var packet = DanaRSPacketGeneralGetShippingInformation(aapsLogger, dateUtil, danaPump)
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        // everything ok :)
        packet = DanaRSPacketGeneralGetShippingInformation(aapsLogger, dateUtil, danaPump)
        Assertions.assertEquals("REVIEW__GET_SHIPPING_INFORMATION", packet.friendlyName)
    }
}