package app.aaps.pump.danaR.comm

import app.aaps.pump.danar.comm.MsgSetBasalProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetBasalProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetBasalProfile(injector, 1.toByte(), Array(24) { 1.0 })

        // test message decoding
        packet.handleMessage(createArray(34, 2.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}