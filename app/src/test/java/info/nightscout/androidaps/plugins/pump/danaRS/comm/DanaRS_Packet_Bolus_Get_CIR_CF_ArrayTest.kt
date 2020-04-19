package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Bolus_Get_CIR_CF_ArrayTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Bolus_Get_CIR_CF_Array(aapsLogger, danaRPump)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(34, 0.toByte()))
        // are pump units MG/DL ???
        Assert.assertEquals(DanaRPump.UNITS_MGDL, danaRPump.units)
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(createArray(34, 3.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BOLUS__GET_CIR_CF_ARRAY", packet.friendlyName)
    }
}