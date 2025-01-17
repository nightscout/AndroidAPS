package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgHistoryAlarm
import org.junit.jupiter.api.Test

class MsgHistoryAlarmTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryAlarm(injector)
        // nothing left to test
    }
}