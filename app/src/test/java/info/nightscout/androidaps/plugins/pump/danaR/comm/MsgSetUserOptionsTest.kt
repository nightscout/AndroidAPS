package info.nightscout.androidaps.plugins.pump.danaR.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSetUserOptionsTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetUserOptions(aapsLogger, danaRPump)
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}