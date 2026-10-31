package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgHistoryDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryDone(injector)
        // nothing left to test
    }
}