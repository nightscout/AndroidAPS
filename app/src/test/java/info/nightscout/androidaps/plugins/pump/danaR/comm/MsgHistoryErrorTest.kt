package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryError
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryErrorTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryError(injector)
        // nothing left to test
    }
}