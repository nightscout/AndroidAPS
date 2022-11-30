package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
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
        Assert.assertEquals("REVIEW__ALL_HISTORY", packet.friendlyName)
    }
}