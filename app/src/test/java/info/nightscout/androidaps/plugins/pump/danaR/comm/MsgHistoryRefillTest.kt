package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryRefill
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryRefillTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryRefill(injector)
        // nothing left to test
    }
}