package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class)
class DanaRS_Packet_Basal_Get_Basal_RateTest : DanaRSTestBase() {


    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Get_Basal_Rate(aapsLogger, rxBus, resourceHelper, danaRPump)
        // test message decoding
        // rate is 0.01
        val array = ByteArray(100)
        putIntToArray(array, 0, (1.0 * 100).toInt())
        putByteToArray(array, 2, (0.05 * 100).toByte())
        packet.handleMessage(array)
        Assert.assertEquals(1.0, danaRPump.maxBasal, 0.0)
        Assert.assertEquals(0.05, danaRPump.basalStep, 0.0)
        Assert.assertTrue(packet.failed)
        Assert.assertEquals("BASAL__GET_BASAL_RATE", packet.friendlyName)
    }
}