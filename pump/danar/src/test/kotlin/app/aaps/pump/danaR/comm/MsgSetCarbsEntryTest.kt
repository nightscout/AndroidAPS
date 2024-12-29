package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgSetCarbsEntry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetCarbsEntryTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetCarbsEntry(injector, System.currentTimeMillis(), 10)

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}