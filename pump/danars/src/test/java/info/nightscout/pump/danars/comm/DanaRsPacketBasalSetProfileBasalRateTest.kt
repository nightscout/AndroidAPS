package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketBasalSetProfileBasalRateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBasalSetProfileBasalRate) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBasalSetProfileBasalRate(packetInjector, 1, createArray(24, 1.0))
        // test params
        val testParams = packet.getRequestParams()
        // is profile 1
        Assert.assertEquals(1.toByte(), testParams[0])
        // is value 100
        Assert.assertEquals(100.toByte(), testParams[3])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__SET_PROFILE_BASAL_RATE", packet.friendlyName)
    }
}