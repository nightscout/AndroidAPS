package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Bolus_Get_Step_Bolus_InformationTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_Step_Bolus_Information(aapsLogger, danaRPump, dateUtil)
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 1.toByte()))
        val valueRequested: Int = (1 and 0x000000FF shl 8) + (1 and 0x000000FF)
        Assert.assertEquals(valueRequested / 100.0, danaRPump.lastBolusAmount, 0.0)
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__GET_STEP_BOLUS_INFORMATION", packet.friendlyName)
    }
}