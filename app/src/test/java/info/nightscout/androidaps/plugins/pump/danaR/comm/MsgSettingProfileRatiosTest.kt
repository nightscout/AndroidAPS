package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSettingProfileRatiosTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingProfileRatios(aapsLogger, danaRPump)
        danaRPump.units = DanaRPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(MessageBase.intFromBuff(createArray(10, 7.toByte()), 0, 2), danaRPump.currentCIR)
    }
}