package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryDone
import org.junit.jupiter.api.Test

class MsgHistoryDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryDone(injector)
        // nothing left to test
    }
}