package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danar.comm.MsgCheckValue
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgCheckValueTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgCheckValue(injector)

        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, DanaPump.EXPORT_MODEL.toByte())
        packet.handleMessage(array)
        Assert.assertEquals(DanaPump.EXPORT_MODEL, danaPump.hwModel)
    }
}