package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingGlucose
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSettingGlucoseTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingGlucose(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(1, danaPump.units)
    }
}