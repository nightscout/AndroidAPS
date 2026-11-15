package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBolusSetExtendedBolusTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketBolusSetExtendedBolus(aapsLogger).with(1.0, 1)
        // test params
        val testParams = packet.getRequestParams()
        Assertions.assertEquals(100.toByte(), testParams[0])
        Assertions.assertEquals(1.toByte(), testParams[2])
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        //        DanaRPump testPump = DanaRPump.getInstance();
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        //        int valueRequested = (((byte) 1 & 0x000000FF) << 8) + (((byte) 1) & 0x000000FF);
//        assertEquals(valueRequested /100d, testPump.lastBolusAmount, 0);
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__SET_EXTENDED_BOLUS", packet.friendlyName)
    }
}