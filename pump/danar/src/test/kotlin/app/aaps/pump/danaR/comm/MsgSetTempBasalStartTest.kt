package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgSetTempBasalStart
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetTempBasalStartTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetTempBasalStart(injector, 250, 1)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}