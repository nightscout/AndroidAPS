package info.nightscout.androidaps.plugins.pump.danaR.comm

import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryDone(aapsLogger, danaRPump)
        // nothing left to test
    }
}