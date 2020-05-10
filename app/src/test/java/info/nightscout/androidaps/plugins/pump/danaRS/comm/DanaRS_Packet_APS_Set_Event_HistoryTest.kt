package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.DateUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest()
class DanaRS_Packet_APS_Set_Event_HistoryTest : DanaRSTestBase() {

    @Test fun runTest() { // test for negative carbs
        val now = DateUtil.now()
        var historyTest = DanaRS_Packet_APS_Set_Event_History(aapsLogger, dateUtil, DanaRPump.CARBS, now, -1, 0)
        var testparams = historyTest.requestParams
        Assert.assertEquals(0.toByte(), testparams[8])
        // 5g carbs
        historyTest = DanaRS_Packet_APS_Set_Event_History(aapsLogger, dateUtil, DanaRPump.CARBS, now, 5, 0)
        testparams = historyTest.requestParams
        Assert.assertEquals(5.toByte(), testparams[8])
        // 150g carbs
        historyTest = DanaRS_Packet_APS_Set_Event_History(aapsLogger, dateUtil, DanaRPump.CARBS, now, 150, 0)
        testparams = historyTest.requestParams
        Assert.assertEquals(150.toByte(), testparams[8])
        // test message generation
        historyTest = DanaRS_Packet_APS_Set_Event_History(aapsLogger, dateUtil, DanaRPump.CARBS, now, 5, 0)
        testparams = historyTest.requestParams
        Assert.assertEquals(5.toByte(), testparams[8])
        Assert.assertEquals(11, testparams.size)
        Assert.assertEquals(DanaRPump.CARBS.toByte(), testparams[0])
        // test message decoding
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, historyTest.failed)
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, historyTest.failed)
        Assert.assertEquals("APS_SET_EVENT_HISTORY", historyTest.friendlyName)
    }
}