package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(RxBusWrapper::class)
class DanaRS_Packet_Bolus_Get_Bolus_OptionTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Bolus_Option(aapsLogger, rxBus, resourceHelper, danaRPump)
        // test message decoding
        //if dataArray is 1 pump.isExtendedBolusEnabled should be true
        packet.handleMessage(createArray(21, 1.toByte()))
        Assert.assertEquals(false, packet.failed)
        //Are options saved to pump
        Assert.assertEquals(false, !danaRPump.isExtendedBolusEnabled)
        Assert.assertEquals(1, danaRPump.bolusCalculationOption)
        Assert.assertEquals(1, danaRPump.missedBolusConfig)
        packet.handleMessage(createArray(21, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        //Are options saved to pump
        Assert.assertEquals(true, !danaRPump.isExtendedBolusEnabled)
        Assert.assertEquals("BOLUS__GET_BOLUS_OPTION", packet.friendlyName)
    }
}