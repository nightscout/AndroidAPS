package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgSetExtendedBolusStop
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetExtendedBolusStopTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetExtendedBolusStop(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}