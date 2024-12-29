package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgPCCommStop
import org.junit.jupiter.api.Test

class MsgPCCommStopTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgPCCommStop(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
    }

}