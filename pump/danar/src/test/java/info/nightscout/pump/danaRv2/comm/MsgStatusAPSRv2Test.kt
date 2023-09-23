package info.nightscout.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MsgStatusAPS_v2
import info.nightscout.pump.danaR.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgStatusAPSRv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgStatusAPS_v2(injector)
        // test iob
        val testArray = createArray(34, 7.toByte())
        val iob = packet.intFromBuff(testArray, 0, 2) / 100.0
        packet.handleMessage(testArray)
        Assertions.assertEquals(iob, danaPump.iob, 0.0)
    }
}