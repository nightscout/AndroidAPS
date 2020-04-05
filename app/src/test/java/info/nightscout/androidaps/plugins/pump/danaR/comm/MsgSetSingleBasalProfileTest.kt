package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgSetSingleBasalProfileTest : DanaRTestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper

    @Test fun runTest() {
        val packet = MsgSetSingleBasalProfile(aapsLogger, RxBusWrapper(), resourceHelper,  createArray(24, 2.0))

        // test message decoding
        packet.handleMessage(createArray(34, 7.toByte()))
        Assert.assertEquals(true, packet.failed)
    }
}