package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaRsPacketHistoryBolusTest : DanaRSTestBase() {

    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Mock lateinit var pumpSync: PumpSync

    @Test fun runTest() {
        val packet = DanaRSPacketHistoryBolus(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump).with(System.currentTimeMillis())
        Assertions.assertEquals("REVIEW__BOLUS", packet.friendlyName)
    }

    private fun packet() = DanaRSPacketHistoryBolus(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump).with(0)

    /** One step bolus record: code 2, 2026-10-01 HH:mm:00, step bolus, [centiUnits] / 100 U */
    private fun bolusRecord(hour: Int, minute: Int, centiUnits: Int) =
        byteArrayOf(0x02, 26, 10, 1, hour.toByte(), minute.toByte(), 0, 0x80.toByte(), (centiUnits shr 8).toByte(), centiUnits.toByte())

    private val header = byteArrayOf(0xB2.toByte(), 0x11)

    @Test fun pumpWithUtcStoresTheTimeInUtc() {
        danaPump.hwModel = 0x09 // Dana-i
        val packet = packet()

        packet.handleMessage(header + bolusRecord(20, 8, 50))

        // Real case: a bolus given at 22:08 CEST was in the pump history as 20:08
        assertThat(packet.danaRHistoryRecord.timestamp).isEqualTo(DateTime(2026, 10, 1, 20, 8, DateTimeZone.UTC).millis)
    }

    @Test fun olderPumpStoresTheTimeInLocalTime() {
        danaPump.hwModel = 0x05 // Dana RS
        val packet = packet()

        packet.handleMessage(header + bolusRecord(20, 8, 50))

        assertThat(packet.danaRHistoryRecord.timestamp).isEqualTo(DateTime(2026, 10, 1, 20, 8, DateTimeZone.getDefault()).millis)
    }
}
