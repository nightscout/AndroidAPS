package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetSingleBasalProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetSingleBasalProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetSingleBasalProfile(injector, createArray(24, 2.0))

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}