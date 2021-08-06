package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRsPacketGeneralGetShippingVerisonTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketGeneralGetShippingVersion) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketGeneralGetShippingVersion(packetInjector)
        // test message decoding
        val ver = byteArrayOf((-78).toByte(), (-127).toByte(), (66).toByte(), (80).toByte(), (78).toByte(), (45).toByte(), (51).toByte(), (46).toByte(), (48).toByte(), (46).toByte(), (48).toByte())
        packet.handleMessage(ver)
        Assert.assertFalse(packet.failed)
        Assert.assertEquals("BPN-3.0.0", danaPump.bleModel)
        Assert.assertEquals("GENERAL__GET_SHIPPING_VERSION", packet.friendlyName)
    }
}