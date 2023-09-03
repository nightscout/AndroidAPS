package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgCheckValue
import info.nightscout.pump.dana.DanaPump
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgCheckValueTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgCheckValue(injector)

        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, DanaPump.EXPORT_MODEL.toByte())
        packet.handleMessage(array)
        Assertions.assertEquals(DanaPump.EXPORT_MODEL, danaPump.hwModel)
    }
}