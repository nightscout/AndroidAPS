package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketOptionSetPumpTimeTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val date = DateTime()
        val packet = DanaRSPacketOptionSetPumpTime(aapsLogger, dateUtil).with(date.millis)
        // test params
        val params = packet.getRequestParams()
        Assertions.assertEquals((date.year - 2000 and 0xff).toByte(), params[0]) // 2019 -> 19
        Assertions.assertEquals((date.monthOfYear and 0xff).toByte(), params[1])
        Assertions.assertEquals((date.dayOfMonth and 0xff).toByte(), params[2])
        Assertions.assertEquals((date.hourOfDay and 0xff).toByte(), params[3])
        Assertions.assertEquals((date.minuteOfHour and 0xff).toByte(), params[4])
        Assertions.assertEquals((date.secondOfMinute and 0xff).toByte(), params[5])
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("OPTION__SET_PUMP_TIME", packet.friendlyName)
    }
}