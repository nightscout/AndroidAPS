package app.aaps.pump.danar.comm

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgBolusStartWithSpeedTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgBolusStartWithSpeed(injector, 0.0, 0)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assertions.assertEquals(true, packet.failed)

        putByteToArray(array, 0, 2)
        packet.handleMessage(array)
        Assertions.assertEquals(false, packet.failed)
    }
}