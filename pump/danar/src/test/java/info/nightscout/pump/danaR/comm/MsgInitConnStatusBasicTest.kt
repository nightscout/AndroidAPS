package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgInitConnStatusBasic
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgInitConnStatusBasicTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgInitConnStatusBasic(injector)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assertions.assertEquals(true, danaPump.pumpSuspended)
    }
}