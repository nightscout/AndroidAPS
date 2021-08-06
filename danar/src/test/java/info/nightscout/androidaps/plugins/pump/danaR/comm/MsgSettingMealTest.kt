package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.comm.MsgSettingMeal
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DanaRPlugin::class, DanaRKoreanPlugin::class)
class MsgSettingMealTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingMeal(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(packet.intFromBuff(createArray(10, 1.toByte()), 0, 1).toDouble() / 100.0, danaPump.bolusStep, 0.0)
    }
}