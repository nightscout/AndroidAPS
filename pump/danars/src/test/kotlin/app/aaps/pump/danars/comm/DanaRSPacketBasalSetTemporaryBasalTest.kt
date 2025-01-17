package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRSPacketBasalSetTemporaryBasalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBasalSetTemporaryBasal) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val testPacket = DanaRSPacketBasalSetTemporaryBasal(packetInjector, 50, 20)
        // params
        val params = testPacket.getRequestParams()
        // is ratio 50
        Assertions.assertEquals(50.toByte(), params[0])
        // is duration 20
        Assertions.assertEquals(20.toByte(), params[1])
        // test message decoding
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, testPacket.failed)
        testPacket.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(true, testPacket.failed)
        Assertions.assertEquals("BASAL__SET_TEMPORARY_BASAL", testPacket.friendlyName)
    }
}