package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetAPSTempBasalStartRv2Test : DanaRTestBase() {

    @Test fun runTest() {

        // test low hard limit
        var packet = MsgSetAPSTempBasalStart_v2(injector, -1, true, false)
        Assert.assertEquals(0, packet.intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test high hard limit
        packet = MsgSetAPSTempBasalStart_v2(injector, 550, true, false)
        Assert.assertEquals(500, packet.intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test setting 15 min
        packet = MsgSetAPSTempBasalStart_v2(injector, 50, true, false)
        Assert.assertEquals(50, packet.intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test setting 30 min
        packet = MsgSetAPSTempBasalStart_v2(injector, 50, false, true)
        Assert.assertEquals(50, packet.intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM30MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // over 200% set always 15 min
        packet = MsgSetAPSTempBasalStart_v2(injector, 250, false, true)
        Assert.assertEquals(250, packet.intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test low hard limit
        packet = MsgSetAPSTempBasalStart_v2(injector, -1, false, true)
        Assert.assertEquals(0, packet.intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM30MIN, packet.intFromBuff(packet.buffer, 2, 1))
        // test high hard limit
        packet = MsgSetAPSTempBasalStart_v2(injector, 550, false, true)
        Assert.assertEquals(500, packet.intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, packet.intFromBuff(packet.buffer, 2, 1))

        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(false, packet.failed)
    }
}