package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRv2.comm.MsgCheckValue_v2
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgCheckValueRv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        val packet = MsgCheckValue_v2(injector)
        // test message decoding
        packet.handleMessage(createArray(34, 3.toByte()))
        Assert.assertEquals(DanaPump.EXPORT_MODEL, danaPump.hwModel)
    }
}