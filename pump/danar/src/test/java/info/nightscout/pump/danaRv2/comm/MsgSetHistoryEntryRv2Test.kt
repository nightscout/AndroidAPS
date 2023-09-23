package info.nightscout.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MsgSetHistoryEntry_v2
import info.nightscout.pump.danaR.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetHistoryEntryRv2Test : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetHistoryEntry_v2(injector, 1, System.currentTimeMillis(), 1, 0)
        // test message decoding
        // != 1 fails
        packet.handleMessage(createArray(34, 2.toByte()))
        Assertions.assertEquals(true, packet.failed)
        // passing an bigger number
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(false, packet.failed)
    }
}