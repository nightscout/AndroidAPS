package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DanaRPlugin::class, DanaRKoreanPlugin::class)
class MsgSettingMealTest : DanaRTestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin

    @Test fun runTest() {
        val packet = MsgSettingMeal(aapsLogger, RxBusWrapper(), resourceHelper, danaRPump, danaRKoreanPlugin)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(MessageBase.intFromBuff(createArray(10, 1.toByte()), 0, 1).toDouble() / 100.0, danaRPump.bolusStep, 0.0)
    }
}