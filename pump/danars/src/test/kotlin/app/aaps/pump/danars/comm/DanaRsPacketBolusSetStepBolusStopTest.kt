package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.whenever

class DanaRsPacketBolusSetStepBolusStopTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        whenever(rh.gs(anyInt())).thenReturn("SomeString")

        danaPump.bolusingDetailedBolusInfo = DetailedBolusInfo()
        val testPacket = DanaRSPacketBolusSetStepBolusStop(aapsLogger, rxBus, rh, danaPump)
        // test message decoding
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, testPacket.failed)
        testPacket.handleMessage(byteArrayOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte(), 1.toByte()))
        Assertions.assertEquals(true, testPacket.failed)
        Assertions.assertEquals("BOLUS__SET_STEP_BOLUS_STOP", testPacket.friendlyName)
    }
}