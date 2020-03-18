package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSetBasalProfileTest : DanaRTestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper

    @Test fun runTest() {
        val packet = MsgSetBasalProfile(aapsLogger, RxBusWrapper(), resourceHelper, 1.toByte(), Array(24) { 1.0 })

        // test message decoding
        packet.handleMessage(createArray(34, 2.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}