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
class DanaRS_Packet_Bolus_Get_Bolus_OptionTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Bolus_Get_Bolus_Option) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Bolus_Option(packetInjector)
        // test message decoding
        //if dataArray is 1 pump.isExtendedBolusEnabled should be true
        packet.handleMessage(createArray(21, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
        //Are options saved to pump
        Assert.assertEquals(false, !danaPump.isExtendedBolusEnabled)
        Assert.assertEquals(1, danaPump.bolusCalculationOption)
        Assert.assertEquals(1, danaPump.missedBolusConfig)
        packet.handleMessage(createArray(21, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        //Are options saved to pump
        Assert.assertEquals(true, !danaPump.isExtendedBolusEnabled)
        Assert.assertEquals("BOLUS__GET_BOLUS_OPTION", packet.friendlyName)
    }
}