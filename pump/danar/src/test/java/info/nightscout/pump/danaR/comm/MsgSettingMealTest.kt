package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingMeal
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingMealTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingMeal(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(packet.intFromBuff(createArray(10, 1.toByte()), 0, 1).toDouble() / 100.0, danaPump.bolusStep, 0.0)
    }
}