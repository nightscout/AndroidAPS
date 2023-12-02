package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryCarbo
import org.junit.jupiter.api.Test

class MsgHistoryCarboTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryCarbo(injector)
        // nothing left to test
    }
}