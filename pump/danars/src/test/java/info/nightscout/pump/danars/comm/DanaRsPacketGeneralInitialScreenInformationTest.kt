package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketGeneralInitialScreenInformationTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketGeneralInitialScreenInformation) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRSPacketGeneralInitialScreenInformation(packetInjector)
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRSPacketGeneralInitialScreenInformation(packetInjector)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals(true, danaPump.pumpSuspended)
        Assert.assertEquals("REVIEW__INITIAL_SCREEN_INFORMATION", packet.friendlyName)
    }
}