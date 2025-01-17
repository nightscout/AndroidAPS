package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryBasalHour
import org.junit.jupiter.api.Test

class MsgHistoryBasalHourTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryBasalHour(injector)
        // nothing left to test
    }
}