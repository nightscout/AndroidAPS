package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketOptionGetPumpTimeTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRSPacketOptionGetPumpTime(aapsLogger, dateUtil, danaPump)
        val array = createArray(8, 0.toByte()) // 6 + 2
        putByteToArray(array, 0, 19) // year 2019
        putByteToArray(array, 1, 2) // month february
        putByteToArray(array, 2, 4) // day 4
        putByteToArray(array, 3, 20) // hour 20
        putByteToArray(array, 4, 11) // min 11
        putByteToArray(array, 5, 35) // second 35

        packet.handleMessage(array)
        Assertions.assertEquals(DateTime(2019, 2, 4, 20, 11, 35).millis, danaPump.pumpTime)
        Assertions.assertEquals("OPTION__GET_PUMP_TIME", packet.friendlyName)
    }

    /**
     * #449: right after the clock went forward, a pump that keeps local time still shows the hour that does not
     * exist. It threw, the reply counted as not received, and the pump was reset: "pump unreachable" at once.
     */
    @Test fun pumpClockInTheDaylightSavingGapIsRead() {
        val defaultZone = DateTimeZone.getDefault()
        try {
            val prague = DateTimeZone.forID("Europe/Prague")
            DateTimeZone.setDefault(prague)
            val packet = DanaRSPacketOptionGetPumpTime(aapsLogger, dateUtil, danaPump)
            val array = createArray(8, 0.toByte()) // 6 + 2
            // 28 March 2027 02:30:00 does not exist in Prague, the clock jumps from 02:00 to 03:00
            putByteToArray(array, 0, 27)
            putByteToArray(array, 1, 3)
            putByteToArray(array, 2, 28)
            putByteToArray(array, 3, 2)
            putByteToArray(array, 4, 30)
            putByteToArray(array, 5, 0)

            packet.handleMessage(array)

            // The pump clock is behind by the gap: the next hour is the real time
            assertThat(danaPump.pumpTime).isEqualTo(DateTime(2027, 3, 28, 3, 30, 0, prague).millis)
        } finally {
            DateTimeZone.setDefault(defaultZone)
        }
    }
}