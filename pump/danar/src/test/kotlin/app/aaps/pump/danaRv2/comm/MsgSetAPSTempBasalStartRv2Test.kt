package app.aaps.pump.danaRv2.comm

import app.aaps.pump.danaR.comm.DanaRTestBase
import app.aaps.pump.danarv2.comm.MsgSetAPSTempBasalStartV2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetAPSTempBasalStartRv2Test : DanaRTestBase() {

    @Test fun runTest() {

        // test low hard limit
        var packet = MsgSetAPSTempBasalStartV2(injector, -1, true, false)
        Assertions.assertEquals(0, packet.intFromBuff(packet.buffer, 0, 2))
        Assertions.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test high hard limit
        packet = MsgSetAPSTempBasalStartV2(injector, 550, true, false)
        Assertions.assertEquals(500, packet.intFromBuff(packet.buffer, 0, 2))
        Assertions.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test setting 15 min
        packet = MsgSetAPSTempBasalStartV2(injector, 50, true, false)
        Assertions.assertEquals(50, packet.intFromBuff(packet.buffer, 0, 2))
        Assertions.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test setting 30 min
        packet = MsgSetAPSTempBasalStartV2(injector, 50, false, true)
        Assertions.assertEquals(50, packet.intFromBuff(packet.buffer, 0, 2))
        Assertions.assertEquals(packet.PARAM30MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // over 200% set always 15 min
        packet = MsgSetAPSTempBasalStartV2(injector, 250, false, true)
        Assertions.assertEquals(250, packet.intFromBuff(packet.buffer, 0, 2))
        Assertions.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test low hard limit
        packet = MsgSetAPSTempBasalStartV2(injector, -1, false, true)
        Assertions.assertEquals(0, packet.intFromBuff(packet.buffer, 0, 2))
        Assertions.assertEquals(packet.PARAM30MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test high hard limit
        packet = MsgSetAPSTempBasalStartV2(injector, 550, false, true)
        Assertions.assertEquals(500, packet.intFromBuff(packet.buffer, 0, 2))
        Assertions.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))

        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(false, packet.failed)
    }
}