package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBasalSetProfileNumberTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketBasalSetProfileNumber(aapsLogger).with(1)
        // test params
        val testParams = packet.getRequestParams()
        // is profile 1
        Assertions.assertEquals(1.toByte(), testParams[0])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BASAL__SET_PROFILE_NUMBER", packet.friendlyName)
    }
}