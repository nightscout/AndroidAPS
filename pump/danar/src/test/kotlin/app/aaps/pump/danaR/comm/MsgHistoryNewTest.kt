package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryNew
import org.junit.jupiter.api.Test

class MsgHistoryNewTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryNew(injector)
        // nothing left to test
    }
}