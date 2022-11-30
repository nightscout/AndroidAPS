package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingUserOptions
import info.nightscout.pump.dana.DanaPump
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSettingUserOptionsTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingUserOptions(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(48, 7.toByte()))
        Assert.assertEquals(7, danaPump.lcdOnTimeSec)
    }
}