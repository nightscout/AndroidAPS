package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(VirtualPumpPlugin::class)
class MsgInitConnStatusOptionTest : DanaRTestBase() {

    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var resourceHelper: ResourceHelper

    @Test fun runTest() {
        `when`(activePlugin.activePump).thenReturn(PowerMockito.mock(VirtualPumpPlugin::class.java))
        val packet = MsgInitConnStatusOption(aapsLogger, RxBusWrapper(), resourceHelper, danaRPump, activePlugin)

        // test message decoding
        packet.handleMessage(createArray(20, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        // message smaller than 21
        packet.handleMessage(createArray(22, 1.toByte()))
        Assert.assertEquals(false, danaRPump.isPasswordOK)
    }
}