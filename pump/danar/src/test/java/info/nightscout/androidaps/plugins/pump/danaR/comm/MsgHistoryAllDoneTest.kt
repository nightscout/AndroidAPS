package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgHistoryAllDone
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgHistoryAllDoneTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgHistoryAllDone(injector)

        // test message decoding
        packet.handleMessage(ByteArray(0))
        Assert.assertEquals(true, danaPump.historyDoneReceived)
    }
}