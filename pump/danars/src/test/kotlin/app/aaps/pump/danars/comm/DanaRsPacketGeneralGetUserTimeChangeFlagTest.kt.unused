package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketGeneralGetUserTimeChangeFlagTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketGeneralGetUserTimeChangeFlag) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRSPacketGeneralGetUserTimeChangeFlag(packetInjector)
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        packet = DanaRSPacketGeneralGetUserTimeChangeFlag(packetInjector)
        packet.handleMessage(createArray(18, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals("REVIEW__GET_USER_TIME_CHANGE_FLAG", packet.friendlyName)
    }
}