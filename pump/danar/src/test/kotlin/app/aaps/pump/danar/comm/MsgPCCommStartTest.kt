package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgPCCommStartTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgPCCommStart(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
    }
}