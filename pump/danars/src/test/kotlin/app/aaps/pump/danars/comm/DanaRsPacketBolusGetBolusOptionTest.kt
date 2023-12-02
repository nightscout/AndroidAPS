package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(false, packet.failed)
        //Are options saved to pump
        Assertions.assertEquals(false, !danaPump.isExtendedBolusEnabled)
        Assertions.assertEquals(1, danaPump.bolusCalculationOption)
        Assertions.assertEquals(1, danaPump.missedBolusConfig)
        packet.handleMessage(createArray(21, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        //Are options saved to pump
        Assertions.assertEquals(true, !danaPump.isExtendedBolusEnabled)
        Assertions.assertEquals("BOLUS__GET_BOLUS_OPTION", packet.friendlyName)
    }
}