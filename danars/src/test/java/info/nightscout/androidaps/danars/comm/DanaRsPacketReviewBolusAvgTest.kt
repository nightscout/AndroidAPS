package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRsPacketReviewBolusAvgTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketReviewBolusAvg) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketReviewBolusAvg(packetInjector)
        // test params
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(12, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        // every average equals 1
        packet.handleMessage(createArray(12, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("REVIEW__BOLUS_AVG", packet.friendlyName)
    }
}