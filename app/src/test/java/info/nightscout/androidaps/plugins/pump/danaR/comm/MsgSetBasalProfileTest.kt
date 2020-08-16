package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MsgSetBasalProfile
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSetBasalProfileTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgSetBasalProfile(injector, 1.toByte(), Array(24) { 1.0 })

        // test message decoding
        packet.handleMessage(createArray(34, 2.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}