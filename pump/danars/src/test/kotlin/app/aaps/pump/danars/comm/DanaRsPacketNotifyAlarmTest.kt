package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaRsPacketNotifyAlarmTest : DanaRSTestBase() {

    @Mock lateinit var pumpSync: PumpSync

    @Test
    fun runTest() {
        val packet = DanaRSPacketNotifyAlarm(aapsLogger, rh, pumpSync, danaPump, uiInteraction)
        // test params
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(17, 0x01.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // no error
        packet.handleMessage(createArray(17, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("NOTIFY__ALARM", packet.friendlyName)
    }
}