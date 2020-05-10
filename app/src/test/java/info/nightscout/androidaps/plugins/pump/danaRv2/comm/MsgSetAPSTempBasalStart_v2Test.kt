package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import info.nightscout.androidaps.danar.comm.MessageBase.intFromBuff
import info.nightscout.androidaps.danars.comm.DanaRS_Packet_APS_Basal_Set_Temporary_Basal.Companion.PARAM30MIN
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSetAPSTempBasalStart_v2Test : DanaRTestBase() {

    @Test fun runTest() {

        // test low hard limit
        var packet = info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2(aapsLogger, -1, true, false)
        Assert.assertEquals(0, intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, intFromBuff(packet.buffer, 2, 1))
        // test high hard limit
        packet = info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2(aapsLogger, 550, true, false)
        Assert.assertEquals(500, intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, intFromBuff(packet.buffer, 2, 1))
        // test setting 15 min
        packet = info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2(aapsLogger, 50, true, false)
        Assert.assertEquals(50, intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, intFromBuff(packet.buffer, 2, 1))
        // test setting 30 min
        packet = info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2(aapsLogger, 50, false, true)
        Assert.assertEquals(50, intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(PARAM30MIN, intFromBuff(packet.buffer, 2, 1))
        // over 200% set always 15 min
        packet = info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2(aapsLogger, 250, false, true)
        Assert.assertEquals(250, intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, intFromBuff(packet.buffer, 2, 1))
        // test low hard limit
        packet = info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2(aapsLogger, -1, false, true)
        Assert.assertEquals(0, intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(PARAM30MIN, intFromBuff(packet.buffer, 2, 1))
        // test high hard limit
        packet = info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2(aapsLogger, 550, false, true)
        Assert.assertEquals(500, intFromBuff(packet.buffer, 0, 2))
        Assert.assertEquals(packet.PARAM15MIN, intFromBuff(packet.buffer, 2, 1))

        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(false, packet.failed)
    }
}