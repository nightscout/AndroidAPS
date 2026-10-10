package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Test

class MsgHistoryAlarmTest : DanaRTestBase() {

    @Test fun runTest() {
        @Suppress("UNUSED_VARIABLE")
        val packet = MsgHistoryAlarm(injector)
        // nothing left to test
    }
}