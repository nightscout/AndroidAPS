package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryGlucose
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryGlucoseTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryGlucose(injector)
        // nothing left to test
    }
}