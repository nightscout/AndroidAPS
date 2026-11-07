package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgHistoryErrorTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryError(injector)
        // nothing left to test
    }
}