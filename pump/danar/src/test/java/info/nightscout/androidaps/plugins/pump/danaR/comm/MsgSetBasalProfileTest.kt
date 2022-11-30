package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetBasalProfile
import org.junit.Assert
import org.junit.jupiter.api.Test

class MsgSetBasalProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetBasalProfile(injector, 1.toByte(), Array(24) { 1.0 })

        // test message decoding
        packet.handleMessage(createArray(34, 2.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}