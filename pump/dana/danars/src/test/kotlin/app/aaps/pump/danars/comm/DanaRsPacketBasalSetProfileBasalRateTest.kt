package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBasalSetProfileBasalRateTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketBasalSetProfileBasalRate(aapsLogger).with(1, createArray(24, 1.0))
        // test params
        val testParams = packet.getRequestParams()
        // is profile 1
        Assertions.assertEquals(1.toByte(), testParams[0])
        // is value 100
        Assertions.assertEquals(100.toByte(), testParams[3])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BASAL__SET_PROFILE_BASAL_RATE", packet.friendlyName)
    }
}