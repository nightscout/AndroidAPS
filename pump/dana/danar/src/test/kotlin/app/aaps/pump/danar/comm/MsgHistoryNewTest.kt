package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgHistoryNewTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryNew(injector)
        // nothing left to test
    }
}