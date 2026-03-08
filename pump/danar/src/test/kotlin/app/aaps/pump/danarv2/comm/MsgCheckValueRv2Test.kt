package app.aaps.pump.danarv2.comm

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.DanaRTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgCheckValueRv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgCheckValueV2(injector)
        // test message decoding
        packet.handleMessage(createArray(34, 3.toByte()))
        Assertions.assertEquals(DanaPump.EXPORT_MODEL, danaPump.hwModel)
    }
}