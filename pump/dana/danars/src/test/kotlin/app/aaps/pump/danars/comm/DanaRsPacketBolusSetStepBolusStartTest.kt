package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBolusSetStepBolusStartTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump)
        // test params
        val testParams = packet.getRequestParams()
        Assertions.assertEquals(0.toByte(), testParams[0])
        Assertions.assertEquals(0.toByte(), testParams[2])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__SET_STEP_BOLUS_START", packet.friendlyName)
    }

    @Test fun amountIsRoundedNotCutOff() {
        // 2.55 * 100 is 254.99999 in floating point, 0.29 * 100 is 28.999999
        val big = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump).with(2.55, 0).getRequestParams()
        val small = DanaRSPacketBolusSetStepBolusStart(aapsLogger, danaPump).with(0.29, 0).getRequestParams()

        Assertions.assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x00, 0x00), big)
        Assertions.assertArrayEquals(byteArrayOf(29, 0x00, 0x00), small)
    }
}
