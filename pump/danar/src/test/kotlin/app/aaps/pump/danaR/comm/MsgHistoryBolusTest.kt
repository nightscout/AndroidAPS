package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryBolus
import org.junit.jupiter.api.Test

class MsgHistoryBolusTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryBolus(injector)
        // nothing left to test
    }
}