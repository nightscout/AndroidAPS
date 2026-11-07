package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaRsPacketHistoryDailyTest : DanaRSTestBase() {

    @Mock lateinit var danaHistoryRecordDao: DanaHistoryRecordDao
    @Mock lateinit var pumpSync: PumpSync

    @Test fun runTest() {
        val packet = DanaRSPacketHistoryDaily(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump).with(System.currentTimeMillis())
        Assertions.assertEquals("REVIEW__DAILY", packet.friendlyName)
    }

    /** A daily total has only a date. Where the clock goes forward at midnight, that midnight does not exist */
    @Test fun dailyTotalOnTheDayTheClockChangesAtMidnightIsRead() {
        val defaultZone = DateTimeZone.getDefault()
        try {
            val havana = DateTimeZone.forID("America/Havana")
            DateTimeZone.setDefault(havana)
            // Cuba moves the clock from 00:00 to 01:00 on the second Sunday of March
            assertThat(havana.isLocalDateTimeGap(LocalDateTime(2027, 3, 14, 0, 0))).isTrue()
            val packet = DanaRSPacketHistoryDaily(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump).with(0)

            // Code 3, 2027-03-14, daily basal and bolus 0
            packet.handleMessage(byteArrayOf(0xB2.toByte(), 0x14, 0x03, 27, 3, 14, 0, 0, 0, 0, 0, 0))

            assertThat(packet.danaRHistoryRecord.timestamp).isEqualTo(DateTime(2027, 3, 14, 1, 0, 0, havana).millis)
        } finally {
            DateTimeZone.setDefault(defaultZone)
        }
    }
}
