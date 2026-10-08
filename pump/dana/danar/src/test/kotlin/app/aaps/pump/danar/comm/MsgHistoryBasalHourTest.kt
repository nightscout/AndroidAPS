package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgHistoryBasalHourTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryBasalHour(injector)
        // nothing left to test
    }
}