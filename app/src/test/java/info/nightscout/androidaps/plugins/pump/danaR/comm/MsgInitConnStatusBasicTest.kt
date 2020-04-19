package info.nightscout.androidaps.plugins.pump.danaR.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgInitConnStatusBasicTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgInitConnStatusBasic(aapsLogger, danaRPump)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assert.assertEquals(true, danaRPump.pumpSuspended)
    }
}