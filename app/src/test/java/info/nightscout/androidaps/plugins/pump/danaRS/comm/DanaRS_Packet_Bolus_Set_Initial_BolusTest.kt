package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Bolus_Set_Initial_BolusTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Set_Initial_Bolus(aapsLogger, 0, 0, 0, 100)
        // test params
        val testparams = packet.requestParams
        Assert.assertEquals(0.toByte(), testparams[0])
        Assert.assertEquals(100.toByte(), testparams[6])
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__SET_BOLUS_RATE", packet.friendlyName)
    }
}