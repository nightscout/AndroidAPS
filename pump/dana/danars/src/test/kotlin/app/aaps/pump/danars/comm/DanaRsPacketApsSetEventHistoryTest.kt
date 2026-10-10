package app.aaps.pump.danars.comm

import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketApsSetEventHistoryTest : DanaRSTestBase() {

    @Test
    fun runTest() { // test for negative carbs
        val now = dateUtil.now()
        var historyTest = DanaRSPacketAPSSetEventHistory(aapsLogger, dateUtil, danaPump).with(DanaPump.HistoryEntry.CARBS.value, now, -1, 0)
        var testParams = historyTest.getRequestParams()
        Assertions.assertEquals(0.toByte(), testParams[8])
        // 5g carbs
        historyTest = DanaRSPacketAPSSetEventHistory(aapsLogger, dateUtil, danaPump).with(DanaPump.HistoryEntry.CARBS.value, now, 5, 0)
        testParams = historyTest.getRequestParams()
        Assertions.assertEquals(5.toByte(), testParams[8])
        // 150g carbs
        historyTest = DanaRSPacketAPSSetEventHistory(aapsLogger, dateUtil, danaPump).with(DanaPump.HistoryEntry.CARBS.value, now, 150, 0)
        testParams = historyTest.getRequestParams()
        Assertions.assertEquals(150.toByte(), testParams[8])
        // test message generation
        historyTest = DanaRSPacketAPSSetEventHistory(aapsLogger, dateUtil, danaPump).with(DanaPump.HistoryEntry.CARBS.value, now, 5, 0)
        testParams = historyTest.getRequestParams()
        Assertions.assertEquals(5.toByte(), testParams[8])
        Assertions.assertEquals(11, testParams.size)
        Assertions.assertEquals(DanaPump.HistoryEntry.CARBS.value.toByte(), testParams[0])
        // test message decoding
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, historyTest.failed)
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(true, historyTest.failed)
        Assertions.assertEquals("APS_SET_EVENT_HISTORY", historyTest.friendlyName)
    }
}