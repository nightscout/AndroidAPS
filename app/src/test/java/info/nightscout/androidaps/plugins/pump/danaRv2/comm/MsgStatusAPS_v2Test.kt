package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import info.nightscout.androidaps.danar.comm.MessageBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgStatusAPS_v2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = info.nightscout.androidaps.danaRv2.comm.MsgStatusAPS_v2(aapsLogger, danaPump)
        // test iob
        //TODO Find a way to mock treatments plugin
        val testArray = createArray(34, 7.toByte())
        val iob = MessageBase.intFromBuff(testArray, 0, 2) / 100.0
        packet.handleMessage(testArray)
        Assert.assertEquals(iob, danaPump.iob, 0.0)
    }
}