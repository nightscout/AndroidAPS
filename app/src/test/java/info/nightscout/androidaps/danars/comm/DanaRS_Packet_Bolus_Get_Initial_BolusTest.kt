package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Bolus_Get_Initial_BolusTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Get_Initial_Bolus) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Initial_Bolus(packetInjector)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        // should fail only if all the initialbolusses are 0
        Assert.assertEquals(false, !packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, !packet.failed)
        Assert.assertEquals("BOLUS__GET_BOLUS_RATE", packet.friendlyName)
    }
}