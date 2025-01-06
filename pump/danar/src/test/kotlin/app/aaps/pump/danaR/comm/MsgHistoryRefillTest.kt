package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryRefill
import org.junit.jupiter.api.Test

class MsgHistoryRefillTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryRefill(injector)
        // nothing left to test
    }
}