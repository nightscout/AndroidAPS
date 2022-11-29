package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
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
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRSPacketGeneralGetUserTimeChangeFlag(packetInjector)
        packet.handleMessage(createArray(18, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("REVIEW__GET_USER_TIME_CHANGE_FLAG", packet.friendlyName)
    }
}