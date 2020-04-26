package info.nightscout.androidaps.plugins.pump.danaR.comm

import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgPCCommStartTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgPCCommStart(aapsLogger)

        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
    }
}