package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class)
class DanaRsPacketBasalGetBasalRateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBasalGetBasalRate) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBasalGetBasalRate(packetInjector)
        // test message decoding
        // rate is 0.01
        val array = ByteArray(100)
        putIntToArray(array, 0, (1.0 * 100).toInt())
        putByteToArray(array, 2, (0.05 * 100).toInt().toByte())
        packet.handleMessage(array)
        Assert.assertEquals(1.0, danaPump.maxBasal, 0.0)
        Assert.assertEquals(0.05, danaPump.basalStep, 0.0)
        Assert.assertTrue(packet.failed)
        Assert.assertEquals("BASAL__GET_BASAL_RATE", packet.friendlyName)
    }
}