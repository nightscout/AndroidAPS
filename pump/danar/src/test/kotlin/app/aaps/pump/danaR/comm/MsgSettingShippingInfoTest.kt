package app.aaps.pump.danaR.comm

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MsgSettingShippingInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingShippingInfoTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingShippingInfo(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(MessageBase.stringFromBuff(createArray(34, 7.toByte()), 0, 10), danaPump.serialNumber)
    }
}