package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetSingleBasalProfile
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetSingleBasalProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetSingleBasalProfile(injector, createArray(24, 2.0))

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}