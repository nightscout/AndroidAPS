package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_General_Get_Shipping_InformationTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_General_Get_Shipping_Information) {
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_Shipping_Information(packetInjector)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        // everything ok :)
        packet = DanaRS_Packet_General_Get_Shipping_Information(packetInjector)
        Assert.assertEquals("REVIEW__GET_SHIPPING_INFORMATION", packet.friendlyName)
    }
}