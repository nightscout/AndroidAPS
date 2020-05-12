package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryNew
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryNewTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryNew(injector)
        // nothing left to test
    }
}