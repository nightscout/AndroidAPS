package app.aaps.pump.danaRv2.comm

import app.aaps.pump.danaR.comm.DanaRTestBase
import app.aaps.pump.danarv2.comm.MsgStatusAPSV2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgStatusAPSRv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgStatusAPSV2(injector)
        // test iob
        val testArray = createArray(34, 7.toByte())
        val iob = packet.intFromBuff(testArray, 0, 2) / 100.0
        packet.handleMessage(testArray)
        Assertions.assertEquals(iob, danaPump.iob, 0.0)
    }
}