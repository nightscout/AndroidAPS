package app.aaps.pump.danars.comm

import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketBolusGetBolusOptionTest : DanaRSTestBase() {

    @Test
    fun runTest() {
        val packet = DanaRSPacketBolusGetBolusOption(aapsLogger, rxBus, rh, danaPump, uiInteraction)
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