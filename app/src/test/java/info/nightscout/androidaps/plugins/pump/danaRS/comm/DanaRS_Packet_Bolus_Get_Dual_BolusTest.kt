package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Bolus_Get_Dual_BolusTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Dual_Bolus(aapsLogger, danaRPump)
        // test params
        Assert.assertEquals(null, packet.requestParams)

        val array = ByteArray(20)
        putByteToArray(array, 0, 1)
        putIntToArray(array, 1, (1.0 * 100).toInt())
        putIntToArray(array, 3, (0.55 * 100).toInt())
        putIntToArray(array, 5, (40.0 * 100).toInt())
        packet.handleMessage(array)
        Assert.assertTrue(packet.failed)
        Assert.assertEquals(1.0, danaRPump.bolusStep, 0.0)
        Assert.assertEquals(0.55, danaRPump.extendedBolusAbsoluteRate, 0.0)
        Assert.assertEquals(40.0, danaRPump.maxBolus, 0.0)

        Assert.assertEquals("BOLUS__GET_DUAL_BOLUS", packet.friendlyName)
    }
}