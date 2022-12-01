package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryNew
import org.junit.jupiter.api.Test

class MsgHistoryNewTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryNew(injector)
        // nothing left to test
    }
}