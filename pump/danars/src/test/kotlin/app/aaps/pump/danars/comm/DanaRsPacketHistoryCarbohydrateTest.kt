package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketHistoryCarbohydrateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRSPacketHistoryCarbohydrate) {
                it.rxBus = rxBus
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketHistoryCarbohydrate(packetInjector, System.currentTimeMillis())
        Assertions.assertEquals("REVIEW__CARBOHYDRATE", packet.friendlyName)
    }
}