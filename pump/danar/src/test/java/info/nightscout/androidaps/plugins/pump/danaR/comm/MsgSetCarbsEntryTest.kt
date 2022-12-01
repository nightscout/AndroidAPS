package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetCarbsEntry
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetCarbsEntryTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetCarbsEntry(injector, System.currentTimeMillis(), 10)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}