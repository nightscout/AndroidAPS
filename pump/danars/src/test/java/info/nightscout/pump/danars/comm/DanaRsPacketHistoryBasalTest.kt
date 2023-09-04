package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketHistoryBasalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRSPacketHistoryBasal) {
                it.rxBus = rxBus
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketHistoryBasal(packetInjector, System.currentTimeMillis())
        Assertions.assertEquals("REVIEW__BASAL", packet.friendlyName)
    }
}