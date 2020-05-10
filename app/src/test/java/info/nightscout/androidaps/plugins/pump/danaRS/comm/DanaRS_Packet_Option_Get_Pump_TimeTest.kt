package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Option_Get_Pump_TimeTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Option_Get_Pump_Time(aapsLogger, danaRPump, dateUtil)
        // test params
        Assert.assertEquals(null, packet.requestParams)
        // test message decoding
        packet.handleMessage(createArray(8, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        // this should fail
        packet.handleMessage(createArray(8, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("OPTION__GET_PUMP_TIME", packet.friendlyName)
    }
}