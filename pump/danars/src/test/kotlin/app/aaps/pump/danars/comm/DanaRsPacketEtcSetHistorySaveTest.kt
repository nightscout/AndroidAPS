package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketEtcSetHistorySaveTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketEtcSetHistorySave) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketEtcSetHistorySave(packetInjector, 0, 0, 0, 0, 0, 0, 0, 0, 2)
        // test params
        val testParams = packet.getRequestParams()
        Assertions.assertEquals(2.toByte(), testParams[8])
        Assertions.assertEquals((2 ushr 8).toByte(), testParams[9])
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("ETC__SET_HISTORY_SAVE", packet.friendlyName)
    }
}