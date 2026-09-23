package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetUserOptionsTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetUserOptions(injector)
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}