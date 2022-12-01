package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgInitConnStatusBasic
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgInitConnStatusBasicTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgInitConnStatusBasic(injector)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assert.assertEquals(true, danaPump.pumpSuspended)
    }
}