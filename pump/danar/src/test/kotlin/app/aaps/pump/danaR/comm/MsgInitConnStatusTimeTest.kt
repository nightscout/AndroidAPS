package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgInitConnStatusTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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
}