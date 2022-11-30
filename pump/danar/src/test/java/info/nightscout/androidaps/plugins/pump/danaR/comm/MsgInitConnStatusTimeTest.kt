package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgInitConnStatusTime
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgInitConnStatusTimeTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgInitConnStatusTime(injector)

        // test message decoding
        packet.handleMessage(createArray(20, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
        // message smaller than 10
        packet.handleMessage(createArray(15, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}