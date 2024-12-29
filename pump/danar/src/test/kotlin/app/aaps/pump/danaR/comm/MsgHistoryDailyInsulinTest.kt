package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryDailyInsulin
import org.junit.jupiter.api.Test

class MsgHistoryDailyInsulinTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryDailyInsulin(injector)
        // nothing left to test
    }
}