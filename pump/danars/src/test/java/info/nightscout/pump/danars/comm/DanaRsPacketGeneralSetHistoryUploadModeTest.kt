package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketGeneralSetHistoryUploadModeTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketGeneralSetHistoryUploadMode) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketGeneralSetHistoryUploadMode(packetInjector, 1)
        // test params
        Assertions.assertEquals(1.toByte(), packet.getRequestParams()[0])
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("REVIEW__SET_HISTORY_UPLOAD_MODE", packet.friendlyName)
    }
}