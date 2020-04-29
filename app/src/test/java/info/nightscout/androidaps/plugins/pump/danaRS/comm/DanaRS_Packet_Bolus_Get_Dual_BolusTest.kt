package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Bolus_Get_Dual_BolusTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Get_Dual_Bolus) {
                it.aapsLogger = aapsLogger
                it.danaRPump = danaRPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Dual_Bolus(packetInjector)
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