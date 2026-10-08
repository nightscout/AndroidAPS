package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.joda.time.DateTime
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
}