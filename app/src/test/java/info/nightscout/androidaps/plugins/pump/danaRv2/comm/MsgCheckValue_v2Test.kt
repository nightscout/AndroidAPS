package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DanaRKoreanPlugin::class, DanaRPlugin::class, DanaRv2Plugin::class, ConfigBuilderPlugin::class)
class MsgCheckValue_v2Test : DanaRTestBase() {

    val rxBus = RxBusWrapper()
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var danaRv2Plugin: DanaRv2Plugin
    @Mock lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Mock lateinit var commandQueue: CommandQueueProvider

    @Test
    fun runTest() {
        val packet = MsgCheckValue_v2(aapsLogger, rxBus, resourceHelper, danaRPump, danaRPlugin, danaRKoreanPlugin, danaRv2Plugin, configBuilderPlugin, commandQueue)
        // test message decoding
        packet.handleMessage(createArray(34, 3.toByte()))
        Assert.assertEquals(DanaRPump.EXPORT_MODEL, danaRPump.model)
    }
}