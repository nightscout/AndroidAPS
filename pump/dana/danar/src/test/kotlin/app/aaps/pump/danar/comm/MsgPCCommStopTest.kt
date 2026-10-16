package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgPCCommStopTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgPCCommStop(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
    }

}