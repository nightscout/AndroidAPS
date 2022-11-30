package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketBolusGetBolusOptionTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusGetBolusOption) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.rh = rh
                it.danaPump = danaPump
                it.uiInteraction = uiInteraction
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBolusGetBolusOption(packetInjector)
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