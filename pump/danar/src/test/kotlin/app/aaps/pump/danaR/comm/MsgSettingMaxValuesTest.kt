package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgSettingMaxValues
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingMaxValuesTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingMaxValues(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(packet.intFromBuff(createArray(10, 7.toByte()), 0, 2).toDouble() / 100.0, danaPump.maxBolus, 0.0)
    }
}