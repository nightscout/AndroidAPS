package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
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
        Assert.assertEquals(1, danaPump.activeProfile)
        Assert.assertEquals("BASAL__GET_PROFILE_NUMBER", packet.friendlyName)
    }
}