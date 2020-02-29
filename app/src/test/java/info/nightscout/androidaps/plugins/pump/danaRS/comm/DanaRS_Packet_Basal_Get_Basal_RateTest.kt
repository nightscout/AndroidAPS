package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class)
class DanaRS_Packet_Basal_Get_Basal_RateTest : DanaRSTestBase() {

    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var resourceHelper: ResourceHelper

    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Get_Basal_Rate(aapsLogger, rxBus, resourceHelper, danaRPump)
        // test message decoding
        // rate is 0.01
        packet.handleMessage(createArray(100, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(100, 5.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__GET_BASAL_RATE", packet.friendlyName)
    }
}