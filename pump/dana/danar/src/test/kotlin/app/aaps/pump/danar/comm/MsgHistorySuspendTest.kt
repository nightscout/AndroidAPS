package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgHistorySuspendTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistorySuspend(injector)
        // nothing left to test
    }
}