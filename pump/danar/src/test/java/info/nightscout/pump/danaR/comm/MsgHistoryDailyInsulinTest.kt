package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryDailyInsulin
import org.junit.jupiter.api.Test

class MsgHistoryDailyInsulinTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryDailyInsulin(injector)
        // nothing left to test
    }
}