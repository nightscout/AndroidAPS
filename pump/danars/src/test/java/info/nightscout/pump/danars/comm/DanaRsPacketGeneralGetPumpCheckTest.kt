package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketGeneralGetPumpCheckTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketGeneralGetPumpCheck) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.rh = rh
                it.danaPump = danaPump
                it.uiInteraction = uiInteraction
            }
        }
    }

    @Test fun runTest() {
        var packet = DanaRSPacketGeneralGetPumpCheck(packetInjector)
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // test for the length message
        packet.handleMessage(createArray(1, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        packet = DanaRSPacketGeneralGetPumpCheck(packetInjector)
        packet.handleMessage(createArray(15, 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals("REVIEW__GET_PUMP_CHECK", packet.friendlyName)
    }
}