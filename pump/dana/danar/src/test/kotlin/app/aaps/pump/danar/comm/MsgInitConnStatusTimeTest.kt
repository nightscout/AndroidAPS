package app.aaps.pump.danar.comm

import app.aaps.core.data.plugin.PluginType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify

class MsgInitConnStatusTimeTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgInitConnStatusTime(injector)

        // test message decoding
        packet.handleMessage(createArray(20, 1.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // message smaller than 10
        packet.handleMessage(createArray(15, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }

    /** The answer of a Korean DanaR is longer. The switch to its driver is the one Configuration makes, so the screens follow it. */
    @Test fun aKoreanPumpSwitchesToTheKoreanDriver() {
        MsgInitConnStatusTime(injector).handleMessage(createArray(20, 1.toByte()))

        verify(configBuilder).performPluginSwitch(danaRKoreanPlugin, true, PluginType.PUMP)
    }
}
