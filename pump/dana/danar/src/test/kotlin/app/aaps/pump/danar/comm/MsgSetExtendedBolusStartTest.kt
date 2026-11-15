package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetExtendedBolusStartTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetExtendedBolusStart(injector, 2.0, 2.toByte())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }

}