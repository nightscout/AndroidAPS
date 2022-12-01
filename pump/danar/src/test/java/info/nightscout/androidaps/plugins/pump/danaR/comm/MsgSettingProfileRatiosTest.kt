package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSettingProfileRatios
import info.nightscout.pump.dana.DanaPump
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSettingProfileRatiosTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSettingProfileRatios(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(packet.intFromBuff(createArray(10, 7.toByte()), 0, 2), danaPump.currentCIR)
    }
}