package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MsgSetHistoryEntry_v2
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetHistoryEntryRv2Test : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetHistoryEntry_v2(injector, 1, System.currentTimeMillis(), 1, 0)
        // test message decoding
        // != 1 fails
        packet.handleMessage(createArray(34, 2.toByte()))
        Assert.assertEquals(true, packet.failed)
        // passing an bigger number
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
    }
}