package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketNotifyMissedBolusAlarmTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketNotifyMissedBolusAlarm(aapsLogger)
        // test params
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(6, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(6, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("NOTIFY__MISSED_BOLUS_ALARM", packet.friendlyName)
    }
}