package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_Basal_Get_Profile_Basal_RateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Basal_Get_Profile_Basal_Rate) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val testPacket = DanaRS_Packet_Basal_Get_Profile_Basal_Rate(packetInjector, 1)
        // test if pumpProfile array is set right
        val basal01 = DanaRS_Packet.byteArrayToInt(DanaRS_Packet(packetInjector).getBytes(createArray(50, 1.toByte()), 2, 2)) / 100.0
        val basal05 = DanaRS_Packet.byteArrayToInt(DanaRS_Packet(packetInjector).getBytes(createArray(50, 5.toByte()), 2, 2)) / 100.0
        val basal12 = DanaRS_Packet.byteArrayToInt(DanaRS_Packet(packetInjector).getBytes(createArray(50, 12.toByte()), 2, 2)) / 100.0
        // basal rate > 1U/hr
        val basal120 = DanaRS_Packet.byteArrayToInt(DanaRS_Packet(packetInjector).getBytes(createArray(50, 120.toByte()), 2, 2)) / 100.0
        val params = testPacket.requestParams
        assertEquals(1.toByte(), params[0])
        testPacket.handleMessage(createArray(50, 0.toByte()))
        assertEquals(0.0, danaPump.pumpProfiles!![1][1], 0.0)
        testPacket.handleMessage(createArray(50, 1.toByte()))
        assertEquals(basal01, danaPump.pumpProfiles!![1][2], 0.0)
        testPacket.handleMessage(createArray(50, 5.toByte()))
        assertEquals(basal05, danaPump.pumpProfiles!![1][1], 0.0)
        testPacket.handleMessage(createArray(50, 12.toByte()))
        assertEquals(basal12, danaPump.pumpProfiles!![1][1], 0.0)
        testPacket.handleMessage(createArray(50, 120.toByte()))
        assertEquals(basal120, danaPump.pumpProfiles!![1][1], 0.0)
        assertEquals("BASAL__GET_PROFILE_BASAL_RATE", testPacket.friendlyName)
    }
}