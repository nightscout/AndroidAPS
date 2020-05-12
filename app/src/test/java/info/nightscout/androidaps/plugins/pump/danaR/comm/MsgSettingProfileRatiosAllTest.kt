package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.danar.comm.MsgSettingProfileRatiosAll
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSettingProfileRatiosAllTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingProfileRatiosAll(aapsLogger, danaPump)
        danaPump.units = info.nightscout.androidaps.dana.DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(MessageBase.intFromBuff(createArray(10, 7.toByte()), 0, 2), danaPump.morningCIR)
    }

}