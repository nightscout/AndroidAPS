package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryAlarm
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgHistoryAlarmTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryAlarm(injector)
        // nothing left to test
    }
}