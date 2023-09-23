package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingUserOptions
import info.nightscout.pump.dana.DanaPump
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingUserOptionsTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingUserOptions(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(48, 7.toByte()))
        Assertions.assertEquals(7, danaPump.lcdOnTimeSec)
    }
}