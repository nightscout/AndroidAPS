package info.nightscout.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetActivateBasalProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgSetActivateBasalProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetActivateBasalProfile(injector, 1.toByte())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assertions.assertEquals(true, packet.failed)
    }
}