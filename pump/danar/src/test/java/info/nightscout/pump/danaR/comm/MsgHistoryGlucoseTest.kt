package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryGlucose
import org.junit.jupiter.api.Test

class MsgHistoryGlucoseTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryGlucose(injector)
        // nothing left to test
    }
}