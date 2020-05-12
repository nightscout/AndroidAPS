package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.comm.MsgInitConnStatusTime
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DanaRKoreanPlugin::class, DanaRPlugin::class, ConfigBuilderPlugin::class, CommandQueueProvider::class)
class MsgInitConnStatusTimeTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgInitConnStatusTime(injector)

        // test message decoding
        packet.handleMessage(createArray(20, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
        // message smaller than 10
        packet.handleMessage(createArray(15, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}