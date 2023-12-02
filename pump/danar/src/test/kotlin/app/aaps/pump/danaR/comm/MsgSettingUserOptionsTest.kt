package app.aaps.pump.danaR.comm

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MsgSettingUserOptions
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