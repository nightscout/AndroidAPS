package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.danar.comm.MsgSettingShippingInfo
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSettingShippingInfoTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingShippingInfo(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(MessageBase.stringFromBuff(createArray(34, 7.toByte()), 0, 10), danaPump.serialNumber)
    }
}