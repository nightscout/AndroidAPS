package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        packet = DanaRSPacketGeneralInitialScreenInformation(packetInjector)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals(true, danaPump.pumpSuspended)
        Assertions.assertEquals("REVIEW__INITIAL_SCREEN_INFORMATION", packet.friendlyName)
    }
}