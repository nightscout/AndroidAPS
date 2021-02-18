package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingBasal
import info.nightscout.androidaps.interfaces.PumpDescription
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(info.nightscout.androidaps.danar.DanaRPlugin::class)
class MsgSettingBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        `when`(danaRPlugin.pumpDescription).thenReturn(PumpDescription())
        val packet = MsgSettingBasal(injector)

        // test message decoding
        packet.handleMessage(createArray(100, 1.toByte()))
        val expected = packet.intFromBuff(createArray(100, 1.toByte()), 2 * 1, 2)
        Assert.assertEquals(expected.toDouble() / 100.0, danaPump.pumpProfiles!![danaPump.activeProfile][1], 0.0)
    }
}