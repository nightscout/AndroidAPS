package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBolusGetCalculationInformationTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBolusGetCalculationInformation) {
                it.aapsLogger = aapsLogger
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBolusGetCalculationInformation(packetInjector)
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(24, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(24, 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BOLUS__GET_CALCULATION_INFORMATION", packet.friendlyName)
    }
}