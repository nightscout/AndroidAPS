package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class)
class DanaRS_Packet_General_Get_Pump_CheckTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_General_Get_Pump_Check) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRS_Packet_General_Get_Pump_Check(packetInjector)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        packet = DanaRS_Packet_General_Get_Pump_Check(packetInjector)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        Assert.assertEquals("REVIEW__GET_PUMP_CHECK", packet.friendlyName)
    }
}