package info.nightscout.androidaps.plugins.pump.danaR.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgStatusBasicTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgStatusBasic(aapsLogger, danaRPump)
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(MessageBase.intFromBuff(createArray(34, 7.toByte()), 0, 3).toDouble() / 750.0, danaRPump.dailyTotalUnits, 0.0)
    }
}