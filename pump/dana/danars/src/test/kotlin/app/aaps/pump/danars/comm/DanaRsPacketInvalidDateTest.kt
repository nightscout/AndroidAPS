package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verifyNoInteractions

/** Dates the pump sends that are not valid must be skipped, not crash the BLE callback */
class DanaRsPacketInvalidDateTest : DanaRSTestBase() {

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage

    private fun apsHistory() =
        DanaRSPacketAPSHistoryEvents(aapsLogger, dateUtil, rxBus, rh, danaPump, detailedBolusInfoStorage, temporaryBasalStorage, preferences, pumpSync)
            .with(dateUtil.now())

    private val header = byteArrayOf(0xB2.toByte(), 0xC2.toByte())

    /** Y M D h m s as the pump sends it, after the packet header (offset 0 is the first data byte) */
    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) =
        header + byteArrayOf(year.toByte(), month.toByte(), day.toByte(), hour.toByte(), minute.toByte(), second.toByte())

    @Test
    fun allZeroApsHistoryRecordIsSkipped() {
        danaPump.hwModel = 0x05 // Dana RS, local time records
        val packet = apsHistory()

        // Real record from #5169: <<<<< APS_HISTORY_EVENTS B2 C2 04 00 00 00 00 00 00 00 00 00 82
        packet.handleMessage(header + byteArrayOf(0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x82.toByte()))
        packet.handleMessage(header + byteArrayOf(0xFF.toByte()))

        assertThat(danaPump.historyDoneReceived).isTrue()
        verifyNoInteractions(pumpSync)
    }

    @Test
    fun validDateIsRead() {
        val time = apsHistory().dateTimeSecFromBuff(date(26, 10, 2, 8, 36, 41), 0)

        assertThat(time).isEqualTo(DateTime(2026, 10, 2, 8, 36, 41).millis)
    }

    @Test
    fun notValidDatesGiveZero() {
        val packet = apsHistory()

        assertThat(packet.dateTimeSecFromBuff(date(0, 0, 0, 0, 0, 0), 0)).isEqualTo(0L)     // all zero
        assertThat(packet.dateTimeSecFromBuff(date(26, 2, 30, 8, 0, 0), 0)).isEqualTo(0L)  // 30 February
        assertThat(packet.dateTimeSecFromBuff(date(26, 10, 2, 190, 0, 0), 0)).isEqualTo(0L) // corrupted hour
        assertThat(packet.dateTimeSecFromBuff(date(26, 10, 2, 8, 60, 0), 0)).isEqualTo(0L)  // minute 60
    }

    @Test
    fun timeInTheDaylightSavingGapMovesToTheNextHour() {
        val defaultZone = DateTimeZone.getDefault()
        try {
            val prague = DateTimeZone.forID("Europe/Prague")
            DateTimeZone.setDefault(prague)

            // 29 March 2026 02:30 does not exist in Prague, the clock jumps from 02:00 to 03:00
            val time = apsHistory().dateTimeSecFromBuff(date(26, 3, 29, 2, 30, 0), 0)

            assertThat(time).isEqualTo(DateTime(2026, 3, 29, 3, 30, 0, prague).millis)
        } finally {
            DateTimeZone.setDefault(defaultZone)
        }
    }

    @Test
    fun pumpWithoutBolusHasNoLastBolus() {
        danaPump.hwModel = 0x0C
        danaPump.lastBolusTime = 1L
        danaPump.lastBolusAmount = 1.0
        val packet = DanaRSPacketBolusGetStepBolusInformation(aapsLogger, dateUtil, danaPump)

        // Real reply of a Dana-i2 that never gave a bolus: last bolus time FF FF, the amount bytes are not valid
        packet.handleMessage(byteArrayOf(0xB2.toByte(), 0x40, 0x00, 0x00, 0xE8.toByte(), 0x03, 0xFF.toByte(), 0xFF.toByte(), 0x24, 0x22, 0xA0.toByte(), 0x0F, 0x0A))

        assertThat(packet.failed).isFalse()
        assertThat(danaPump.lastBolusTime).isNull()
        assertThat(danaPump.lastBolusAmount).isNull()
        assertThat(danaPump.maxBolus).isEqualTo(40.0)
        assertThat(danaPump.bolusStep).isEqualTo(0.1)
    }
}
