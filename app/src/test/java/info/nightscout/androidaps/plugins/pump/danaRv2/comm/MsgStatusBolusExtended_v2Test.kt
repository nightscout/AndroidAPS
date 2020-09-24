package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MsgStatusBolusExtended_v2
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgStatusBolusExtended_v2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgStatusBolusExtended_v2(injector)
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(packet.intFromBuff(createArray(10, 7.toByte()), 2, 2).toDouble() / 100.0, danaPump.extendedBolusAmount, 0.0)
    }
}