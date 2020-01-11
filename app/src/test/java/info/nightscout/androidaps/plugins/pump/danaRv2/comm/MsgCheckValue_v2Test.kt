package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.AAPSMocker
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.utils.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(MainApp::class, SP::class, L::class, ConfigBuilderPlugin::class, CommandQueue::class, DanaRKoreanPlugin::class, DanaRPlugin::class, DanaRv2Plugin::class)
class MsgCheckValue_v2Test {

    @Test fun runTest() {
        val packet = MsgCheckValue_v2()
        // test message decoding
        packet.handleMessage(createArray(34, 3.toByte()))
        val pump = DanaRPump.getInstance()
        Assert.assertEquals(DanaRPump.EXPORT_MODEL.toLong(), pump.model.toLong())
    }

    fun createArray(length: Int, fillWith: Byte): ByteArray {
        val ret = ByteArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    fun createArray(length: Int, fillWith: Double): DoubleArray {
        val ret = DoubleArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    @Before
    fun mock() {
        AAPSMocker.mockMainApp()
        AAPSMocker.mockApplicationContext()
        AAPSMocker.mockSP()
        AAPSMocker.mockL()
        AAPSMocker.mockConfigBuilder()
        AAPSMocker.mockCommandQueue()

        PowerMockito.mockStatic(DanaRKoreanPlugin::class.java)
        val drk = PowerMockito.mock(DanaRKoreanPlugin::class.java)
        PowerMockito.`when`(DanaRKoreanPlugin.getPlugin()).thenReturn(drk)

        PowerMockito.mockStatic(DanaRPlugin::class.java)
        val dr = PowerMockito.mock(DanaRPlugin::class.java)
        PowerMockito.`when`(DanaRPlugin.getPlugin()).thenReturn(dr)

        PowerMockito.mockStatic(DanaRv2Plugin::class.java)
        val drv2 = PowerMockito.mock(DanaRv2Plugin::class.java)
        PowerMockito.`when`(DanaRv2Plugin.getPlugin()).thenReturn(drv2)
    }
}