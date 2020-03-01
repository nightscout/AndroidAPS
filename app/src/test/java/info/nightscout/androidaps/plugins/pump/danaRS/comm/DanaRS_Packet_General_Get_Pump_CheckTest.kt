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
class DanaRS_Packet_General_Get_Pump_CheckTest : DanaRSTestBase() {

    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var resourceHelper: ResourceHelper

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_Pump_Check(aapsLogger, danaRPump, rxBus, resourceHelper)
        // test params
        val testparams = packet.requestParams
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
// test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        // everything ok :)
        packet = DanaRS_Packet_General_Get_Pump_Check(aapsLogger, danaRPump, rxBus, resourceHelper)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        //        packet.handleMessage(createArray(15, (byte) 161));
//        assertEquals(true, packet.failed);
        Assert.assertEquals("REVIEW__GET_PUMP_CHECK", packet.friendlyName)
    }
}