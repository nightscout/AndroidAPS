package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingProfileRatiosAll
import info.nightscout.pump.dana.DanaPump
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSettingProfileRatiosAllTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingProfileRatiosAll(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(packet.intFromBuff(createArray(10, 7.toByte()), 0, 2), danaPump.morningCIR)
    }

}