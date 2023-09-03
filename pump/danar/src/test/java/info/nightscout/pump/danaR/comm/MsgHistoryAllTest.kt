package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgHistoryAllTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryAll(injector)

        // test message decoding
        val array = createArray(100, 2)

        putByteToArray(array, 0, 1)
        packet.handleMessage(array)
        Assertions.assertEquals(false, packet.failed)
        // passing an bigger number
        putByteToArray(array, 0, 17)
        packet.handleMessage(array)
        Assertions.assertEquals(true, packet.failed)
    }
}