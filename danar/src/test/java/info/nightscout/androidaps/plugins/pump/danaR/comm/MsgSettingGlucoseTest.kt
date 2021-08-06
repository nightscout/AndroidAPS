package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingGlucose
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSettingGlucoseTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingGlucose(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(1, danaPump.units)
    }
}