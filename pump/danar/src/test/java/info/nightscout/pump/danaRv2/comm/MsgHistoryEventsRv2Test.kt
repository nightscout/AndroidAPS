package info.nightscout.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MsgHistoryEventsV2
import info.nightscout.pump.danaR.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgHistoryEventsRv2Test : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryEventsV2(injector, 0)

        // test message decoding
        val array = createArray(100, 2)

        putByteToArray(array, 0, 0x01.toByte())
        packet.handleMessage(array)
        Assertions.assertEquals(false, danaPump.historyDoneReceived)

        putByteToArray(array, 0, 0xFF.toByte())
        packet.handleMessage(array)
        Assertions.assertEquals(true, danaPump.historyDoneReceived)
    }
}