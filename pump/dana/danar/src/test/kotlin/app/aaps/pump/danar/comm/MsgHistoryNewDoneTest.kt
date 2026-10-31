package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgHistoryNewDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryNewDone(injector)
        // nothing left to test
    }
}