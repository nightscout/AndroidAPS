package app.aaps.pump.danar.comm

import app.aaps.core.data.time.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgStatusTempBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgStatusTempBasal(injector)
        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(true, packet.isTempBasalInProgress)
        // passing an bigger number
        packet.handleMessage(createArray(34, 2.toByte()))
        Assertions.assertEquals(false, packet.isTempBasalInProgress)
    }

    @Test fun anApsTempBasalOf30MinutesIsRead() {
        danaPump.tempBasalStart = 0
        // a 6 byte header, then: running (0x01) as an APS temp basal (0x02), 150 %, the code 160 = 30 minutes, 60 s run
        val bytes = byteArrayOf(0, 0, 0, 0, 0, 0, 0x03, 150.toByte(), 160.toByte(), 60, 0, 0)

        MsgStatusTempBasal(injector).handleMessage(bytes)

        Assertions.assertEquals(T.mins(30).msecs(), danaPump.tempBasalDuration)
        Assertions.assertEquals(150, danaPump.tempBasalPercent)
    }

    /** The loop can set a zero temp 2 s after a high one. The status must give the new percent, though the starts are close. */
    @Test fun anotherTempBasalThatStartsWithin3SecondsIsRead() {
        danaPump.tempBasalStart = 0
        // 200 % for 30 minutes, run 2 s, then 0 % for 30 minutes, just started
        MsgStatusTempBasal(injector).handleMessage(byteArrayOf(0, 0, 0, 0, 0, 0, 0x03, 200.toByte(), 160.toByte(), 2, 0, 0))
        Assertions.assertEquals(200, danaPump.tempBasalPercent)

        MsgStatusTempBasal(injector).handleMessage(byteArrayOf(0, 0, 0, 0, 0, 0, 0x03, 0, 160.toByte(), 0, 0, 0))

        Assertions.assertEquals(0, danaPump.tempBasalPercent)
        Assertions.assertEquals(now, danaPump.tempBasalStart)
    }

    /** The start worked out from the running seconds can be a second off: the same temp basal keeps its start. */
    @Test fun theSameTempBasalKeepsItsStart() {
        danaPump.tempBasalStart = 0
        MsgStatusTempBasal(injector).handleMessage(byteArrayOf(0, 0, 0, 0, 0, 0, 0x03, 150.toByte(), 160.toByte(), 60, 0, 0))
        val start = danaPump.tempBasalStart

        MsgStatusTempBasal(injector).handleMessage(byteArrayOf(0, 0, 0, 0, 0, 0, 0x03, 150.toByte(), 160.toByte(), 61, 0, 0))

        Assertions.assertEquals(start, danaPump.tempBasalStart)
    }

    @Test fun aTempBasalInHoursIsRead() {
        danaPump.tempBasalStart = 0
        val bytes = byteArrayOf(0, 0, 0, 0, 0, 0, 0x01, 50, 2, 60, 0, 0)

        MsgStatusTempBasal(injector).handleMessage(bytes)

        Assertions.assertEquals(T.hours(2).msecs(), danaPump.tempBasalDuration)
    }
}
