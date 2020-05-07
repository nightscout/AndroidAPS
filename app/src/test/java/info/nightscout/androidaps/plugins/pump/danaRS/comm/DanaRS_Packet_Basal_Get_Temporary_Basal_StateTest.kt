package info.nightscout.androidaps.plugins.pump.danaRS.comm

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_Basal_Get_Temporary_Basal_StateTest : DanaRSTestBase() {

    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump, dateUtil)
        // test message decoding
        val array = ByteArray(100)
        putByteToArray(array, 0, 1.toByte())
        putByteToArray(array, 1, 1.toByte())
        putByteToArray(array, 2, 230.toByte())
        putByteToArray(array, 3, 150.toByte())
        putIntToArray(array, 4, 1)
        packet.handleMessage(array)
        Assert.assertTrue(packet.failed)
        Assert.assertTrue(danaRPump.isTempBasalInProgress)
        Assert.assertEquals(300, danaRPump.tempBasalPercent)
        Assert.assertEquals(15 * 60, danaRPump.tempBasalTotalSec)
        Assert.assertEquals("BASAL__TEMPORARY_BASAL_STATE", packet.friendlyName)
    }
}