package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketGeneralGetShippingInformationTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRSPacketGeneralGetShippingInformation) {
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRSPacketGeneralGetShippingInformation(packetInjector)
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        // everything ok :)
        packet = DanaRSPacketGeneralGetShippingInformation(packetInjector)
        Assert.assertEquals("REVIEW__GET_SHIPPING_INFORMATION", packet.friendlyName)
    }
}