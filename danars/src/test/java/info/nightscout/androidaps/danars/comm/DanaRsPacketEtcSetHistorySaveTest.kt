package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
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
        Assert.assertEquals(2.toByte(), testParams[8])
        Assert.assertEquals((2 ushr 8).toByte(), testParams[9])
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("ETC__SET_HISTORY_SAVE", packet.friendlyName)
    }
}