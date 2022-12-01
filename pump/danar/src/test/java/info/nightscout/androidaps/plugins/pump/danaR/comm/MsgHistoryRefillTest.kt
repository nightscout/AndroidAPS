package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryRefill
import org.junit.jupiter.api.Test

class MsgHistoryRefillTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryRefill(injector)
        // nothing left to test
    }
}