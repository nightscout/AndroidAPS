package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(12, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // every average equals 1
        packet.handleMessage(createArray(12, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("REVIEW__BOLUS_AVG", packet.friendlyName)
    }
}