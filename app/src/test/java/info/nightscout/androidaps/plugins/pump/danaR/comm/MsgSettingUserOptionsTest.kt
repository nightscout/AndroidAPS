package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.dana.DanaRPump
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSettingUserOptionsTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingUserOptions(aapsLogger, danaRPump)
        danaRPump.units = info.nightscout.androidaps.dana.DanaRPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(48, 7.toByte()))
        Assert.assertEquals(7, danaRPump.lcdOnTimeSec)
    }
}