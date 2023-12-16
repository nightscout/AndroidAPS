package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgPCCommStart
import org.junit.jupiter.api.Test

class MsgPCCommStartTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgPCCommStart(injector)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
    }
}