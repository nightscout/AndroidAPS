package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingPumpTime
import info.nightscout.pump.dana.DanaPump
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingPumpTimeTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingPumpTime(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        val bytes = createArray(34, 7.toByte())
        val time = DateTime(
            2000 + packet.intFromBuff(bytes, 5, 1),
            packet.intFromBuff(bytes, 4, 1),
            packet.intFromBuff(bytes, 3, 1),
            packet.intFromBuff(bytes, 2, 1),
            packet.intFromBuff(bytes, 1, 1),
            packet.intFromBuff(bytes, 0, 1)
        ).millis
        packet.handleMessage(bytes)
        Assertions.assertEquals(time, danaPump.getPumpTime())
    }
}