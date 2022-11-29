package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetActivateBasalProfile
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetActivateBasalProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetActivateBasalProfile(injector, 1.toByte())

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}