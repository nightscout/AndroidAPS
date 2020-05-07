package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_History_DailyTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_History_Daily(aapsLogger, rxBus, dateUtil, System.currentTimeMillis())
        Assert.assertEquals("REVIEW__DAILY", packet.friendlyName)
    }
}