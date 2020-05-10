package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.utils.DateUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Option_Set_Pump_TimeTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Option_Set_Pump_Time(aapsLogger, dateUtil, DateUtil.now())
        // test params
        val params = packet.requestParams
        Assert.assertEquals((Date().date and 0xff).toByte(), params[2])
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("OPTION__SET_PUMP_TIME", packet.friendlyName)
    }
}