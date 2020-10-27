package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryBasalHour
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryBasalHourTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryBasalHour(injector)
        // nothing left to test
    }
}