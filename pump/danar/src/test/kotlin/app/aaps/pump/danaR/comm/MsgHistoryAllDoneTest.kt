package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryAllDone
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgHistoryAllDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryAllDone(injector)

        // test message decoding
        packet.handleMessage(ByteArray(0))
        Assertions.assertEquals(true, danaPump.historyDoneReceived)
    }
}