package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetTime
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetTimeTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetTime(injector, System.currentTimeMillis())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}