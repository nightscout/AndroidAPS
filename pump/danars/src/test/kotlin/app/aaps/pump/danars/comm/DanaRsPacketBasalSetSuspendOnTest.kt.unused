package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBasalSetSuspendOnTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBasalSetSuspendOn) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBasalSetSuspendOn(packetInjector)
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BASAL__SET_SUSPEND_ON", packet.friendlyName)
    }
}