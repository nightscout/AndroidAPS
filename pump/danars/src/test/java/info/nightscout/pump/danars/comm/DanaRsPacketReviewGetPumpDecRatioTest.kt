package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketReviewGetPumpDecRatioTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketReviewGetPumpDecRatio) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketReviewGetPumpDecRatio(packetInjector)

        val array = ByteArray(100)
        putByteToArray(array, 0, 4.toByte())
        packet.handleMessage(array)
        Assertions.assertEquals(20, danaPump.decRatio)
        Assertions.assertEquals("REVIEW__GET_PUMP_DEC_RATIO", packet.friendlyName)
    }
}