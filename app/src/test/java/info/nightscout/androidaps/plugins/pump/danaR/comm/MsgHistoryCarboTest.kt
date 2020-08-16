package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryCarbo
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryCarboTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryCarbo(injector)
        // nothing left to test
    }
}