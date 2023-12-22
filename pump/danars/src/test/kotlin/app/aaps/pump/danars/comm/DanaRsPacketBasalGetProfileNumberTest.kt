package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBasalGetProfileNumberTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBasalGetProfileNumber) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBasalGetProfileNumber(packetInjector)

        val array = ByteArray(100)
        putByteToArray(array, 0, 1.toByte())
        packet.handleMessage(array)
        Assertions.assertEquals(1, danaPump.activeProfile)
        Assertions.assertEquals("BASAL__GET_PROFILE_NUMBER", packet.friendlyName)
    }
}