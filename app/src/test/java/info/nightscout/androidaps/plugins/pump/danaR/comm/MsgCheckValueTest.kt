package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.comm.MsgCheckValue
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgCheckValueTest : DanaRTestBase() {

    @Mock lateinit var danaRPlugin: DanaRPlugin

    @Test fun runTest() {
        val packet = MsgCheckValue(aapsLogger, danaPump, danaRPlugin)

        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, info.nightscout.androidaps.dana.DanaPump.EXPORT_MODEL.toByte())
        packet.handleMessage(array)
        Assert.assertEquals(info.nightscout.androidaps.dana.DanaPump.EXPORT_MODEL, danaPump.hwModel)
    }
}