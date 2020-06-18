package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Basal_Set_Basal_RateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Basal_Set_Basal_Rate) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        // test message decoding
        val packet = DanaRS_Packet_Basal_Set_Basal_Rate(packetInjector, createArray(24, 5.0))
        val requested = packet.requestParams
        var lookingFor = (5 * 100 and 0xff).toByte()
        Assert.assertEquals(lookingFor, requested[24])
        lookingFor = (500 ushr 8 and 0xff).toByte()
        Assert.assertEquals(lookingFor, requested[25])
        packet.handleMessage(createArray(3, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(3, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__SET_BASAL_RATE", packet.friendlyName)
    }
}