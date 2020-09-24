package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Get_Today_Delivery_TotalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_General_Get_Today_Delivery_Total) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_Today_Delivery_Total(packetInjector)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRS_Packet_General_Get_Today_Delivery_Total(packetInjector)
        packet.handleMessage(createArray(18, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(15, 1.toByte()))
        Assert.assertEquals(((1 and 0x000000FF shl 8) + (1 and 0x000000FF)) / 100.0, danaPump.dailyTotalUnits, 0.0)
        Assert.assertEquals("REVIEW__GET_TODAY_DELIVERY_TOTAL", packet.friendlyName)
    }
}