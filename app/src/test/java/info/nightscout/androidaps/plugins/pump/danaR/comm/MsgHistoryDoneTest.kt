package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryDone
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryDone(injector)
        // nothing left to test
    }
}