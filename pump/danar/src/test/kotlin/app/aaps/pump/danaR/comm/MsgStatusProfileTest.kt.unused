package app.aaps.pump.danaR.comm

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MsgStatusProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgStatusProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgStatusProfile(injector)
        danaPump.units = DanaPump.UNITS_MGDL
        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(packet.intFromBuff(createArray(10, 7.toByte()), 0, 2), danaPump.currentCIR)
    }
}