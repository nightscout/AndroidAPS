package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class)
class DanaRsPacketGeneralGetPumpCheckTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketGeneralGetPumpCheck) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRSPacketGeneralGetPumpCheck(packetInjector)
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRSPacketGeneralGetPumpCheck(packetInjector)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("REVIEW__GET_PUMP_CHECK", packet.friendlyName)
    }
}