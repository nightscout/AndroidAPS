package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryDailyInsulin
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryDailyInsulinTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryDailyInsulin(injector)
        // nothing left to test
    }
}