package app.aaps.pump.danaR.comm

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MsgSettingProfileRatios
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingProfileRatiosTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingProfileRatios(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(packet.intFromBuff(createArray(10, 7.toByte()), 0, 2), danaPump.currentCIR)
    }
}