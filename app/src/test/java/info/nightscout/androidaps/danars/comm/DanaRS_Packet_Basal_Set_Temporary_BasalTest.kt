package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Basal_Set_Temporary_BasalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Basal_Set_Temporary_Basal) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val testPacket = DanaRS_Packet_Basal_Set_Temporary_Basal(packetInjector, 50, 20)
        // params
        val params = testPacket.requestParams
        // is ratio 50
        Assert.assertEquals(50.toByte(), params[0])
        // is duration 20
        Assert.assertEquals(20.toByte(), params[1])
        // test message decoding
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, testPacket.failed)
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, testPacket.failed)
        Assert.assertEquals("BASAL__SET_TEMPORARY_BASAL", testPacket.friendlyName)
    }
}