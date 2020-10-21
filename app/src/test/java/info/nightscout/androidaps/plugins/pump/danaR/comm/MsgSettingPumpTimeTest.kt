package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danar.comm.MsgSettingPumpTime
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
class MsgSettingPumpTimeTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingPumpTime(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        val bytes = createArray(34, 7.toByte())
        val time = Date(
            100 + packet.intFromBuff(bytes, 5, 1),
            packet.intFromBuff(bytes, 4, 1) - 1,
            packet.intFromBuff(bytes, 3, 1),
            packet.intFromBuff(bytes, 2, 1),
            packet.intFromBuff(bytes, 1, 1),
            packet.intFromBuff(bytes, 0, 1)
        ).time
        packet.handleMessage(bytes)
        Assert.assertEquals(time, danaPump.getPumpTime())
    }
}