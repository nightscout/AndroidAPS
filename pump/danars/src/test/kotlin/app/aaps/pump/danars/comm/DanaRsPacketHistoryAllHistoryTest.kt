package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketHistoryAllHistoryTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRSPacketHistoryAllHistory) {
                it.rxBus = rxBus
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketHistoryAllHistory(packetInjector, System.currentTimeMillis())
        Assertions.assertEquals("REVIEW__ALL_HISTORY", packet.friendlyName)
    }
}