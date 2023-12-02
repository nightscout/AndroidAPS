package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgInitConnStatusOption
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgInitConnStatusOptionTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgInitConnStatusOption(injector)

        // test message decoding
        packet.handleMessage(createArray(20, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        // message smaller than 21
        packet.handleMessage(createArray(22, 1.toByte()))
        Assertions.assertEquals(false, danaPump.isPasswordOK)
    }
}