package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaRsPacketHistoryBasalTest : DanaRSTestBase() {

    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Mock lateinit var pumpSync: PumpSync

    @Test
    fun runTest() {
        val packet = DanaRSPacketHistoryBasal(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump).with(System.currentTimeMillis())
        Assertions.assertEquals("REVIEW__BASAL", packet.friendlyName)
    }
}