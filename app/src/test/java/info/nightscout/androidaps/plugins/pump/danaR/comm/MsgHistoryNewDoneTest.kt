package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryNewDone
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryNewDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryNewDone(injector)
        // nothing left to test
    }
}