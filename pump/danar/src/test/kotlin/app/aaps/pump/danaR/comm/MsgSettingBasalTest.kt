package app.aaps.pump.danaR.comm

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.pump.danar.comm.MsgSettingBasal
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class MsgSettingBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        whenever(danaRPlugin.pumpDescription).thenReturn(PumpDescription())
        val packet = MsgSettingBasal(injector)

        // test message decoding
        packet.handleMessage(createArray(100, 1.toByte()))
        val expected = packet.intFromBuff(createArray(100, 1.toByte()), 2 * 1, 2)
        Assertions.assertEquals(expected.toDouble() / 100.0, danaPump.pumpProfiles!![danaPump.activeProfile][1], 0.0)
    }
}