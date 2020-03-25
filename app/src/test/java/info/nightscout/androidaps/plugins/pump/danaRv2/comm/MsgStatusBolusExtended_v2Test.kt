package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgStatusBolusExtended_v2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgStatusBolusExtended_v2(aapsLogger, danaRPump)
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(MessageBase.intFromBuff(createArray(10, 7.toByte()), 2, 2).toDouble() / 100.0, danaRPump.extendedBolusAmount, 0.0)
    }
}