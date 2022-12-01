package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRSPacketBasalSetTemporaryBasalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBasalSetTemporaryBasal) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val testPacket = DanaRSPacketBasalSetTemporaryBasal(packetInjector, 50, 20)
        // params
        val params = testPacket.getRequestParams()
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