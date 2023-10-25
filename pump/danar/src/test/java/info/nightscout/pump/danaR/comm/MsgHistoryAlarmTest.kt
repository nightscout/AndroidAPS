package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryAlarm
import org.junit.jupiter.api.Test

class MsgHistoryAlarmTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryAlarm(injector)
        // nothing left to test
    }
}