package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgInitConnStatusOption
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(VirtualPumpPlugin::class)
class MsgInitConnStatusOptionTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(activePluginProvider.activePump).thenReturn(PowerMockito.mock(VirtualPumpPlugin::class.java))
        val packet = MsgInitConnStatusOption(injector)

        // test message decoding
        packet.handleMessage(createArray(20, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        // message smaller than 21
        packet.handleMessage(createArray(22, 1.toByte()))
        Assert.assertEquals(false, danaPump.isPasswordOK)
    }
}