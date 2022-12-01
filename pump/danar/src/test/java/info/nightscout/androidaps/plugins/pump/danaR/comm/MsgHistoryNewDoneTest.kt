package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryNewDone
import org.junit.jupiter.api.Test

class MsgHistoryNewDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryNewDone(injector)
        // nothing left to test
    }
}