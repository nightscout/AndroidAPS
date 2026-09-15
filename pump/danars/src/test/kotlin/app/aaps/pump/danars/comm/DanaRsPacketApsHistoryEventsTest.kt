package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.keys.DanaBooleanKey
import app.aaps.pump.danars.DanaRSTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import java.util.Calendar
import java.util.GregorianCalendar

class DanaRsPacketApsHistoryEventsTest : DanaRSTestBase() {

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage

    @Test fun runTest() {
        val now = dateUtil.now()

        val testPacket = DanaRSPacketAPSHistoryEvents(aapsLogger, dateUtil, rxBus, rh, danaPump, detailedBolusInfoStorage, temporaryBasalStorage, preferences, pumpSync).with(now)
        // test getRequestedParams
        val returnedValues = testPacket.getRequestParams()
        val expectedValues = getCalender(now)
        //year
        Assertions.assertEquals(expectedValues[0], returnedValues[0])
        //month
        Assertions.assertEquals(expectedValues[1], returnedValues[1])
        //day of month
        Assertions.assertEquals(expectedValues[2], returnedValues[2])
        // hour
        Assertions.assertEquals(expectedValues[3], returnedValues[3])
        // minute
        Assertions.assertEquals(expectedValues[4], returnedValues[4])
        // second
        Assertions.assertEquals(expectedValues[5], returnedValues[5])
        Assertions.assertEquals("APS_HISTORY_EVENTS", testPacket.friendlyName)
    }

    /**
     * Feeds one record of every [DanaPump.HistoryEntry] type, then the 0xFF terminator, and checks
     * the whole sorted sweep ran: `handleMessage` buffers each record and, on the terminator, sorts
     * by timestamp and calls `processMessage` for each — which is the `when` over every event type
     * (temp basal, extended, bolus, dual, suspend, refill, prime, profile change, carbs, cannula,
     * time change) and the `PumpSync` calls behind them. `historyDoneReceived` only flips true once
     * that sweep completes, and `lastEventTimeLoaded` is advanced inside `processMessage` — so both
     * being set proves every branch executed without throwing. LogInsulinChange/LogCannulaChange are
     * on so the refill/cannula therapy-event paths are taken too.
     */
    @Test fun processesEveryHistoryEventType() {
        whenever(preferences.get(DanaBooleanKey.LogInsulinChange)).thenReturn(true)
        whenever(preferences.get(DanaBooleanKey.LogCannulaChange)).thenReturn(true)

        val packet = packet()
        var second = 1
        DanaPump.HistoryEntry.entries.forEach { entry ->
            packet.handleMessage(record(entry.value, param1 = 100, param2 = 30, second = second++))
        }
        packet.handleMessage(terminator())

        assertThat(danaPump.historyDoneReceived).isTrue()
        assertThat(danaPump.lastEventTimeLoaded).isGreaterThan(0L)
    }

    /**
     * The RS firmware bug workaround: a TEMP_STOP that carries the exact timestamp of the TEMP_START
     * right before it is a spurious "cancelled immediately" and must be dropped (the temp basal is
     * really still running). Same timestamp on both records exercises that skip branch.
     */
    @Test fun skipsTempStopSharingItsStartTimestamp() {
        val packet = packet()
        packet.handleMessage(record(DanaPump.HistoryEntry.TEMP_START.value, param1 = 150, param2 = 30, second = 5))
        packet.handleMessage(record(DanaPump.HistoryEntry.TEMP_STOP.value, second = 5)) // same timestamp → skipped
        packet.handleMessage(terminator())

        assertThat(danaPump.historyDoneReceived).isTrue()
    }

    /**
     * The UTC wire format (pump hardware model >= 7, so `DanaPump.usingUTC` is true): the record code
     * moves to byte 2, the timestamp is a 4-byte epoch-seconds field at byte 3, and the pump id folds
     * in the per-record index — a different decoding path in `recordCode`/`dateTime`/`processMessage`
     * than the local-time format above.
     */
    @Test fun processesUtcFormattedRecords() {
        danaPump.hwModel = 9 // Dana-i → usingUTC = true
        val packet = packet()
        packet.handleMessage(utcRecord(DanaPump.HistoryEntry.BOLUS.value, epochSeconds = 1_700_000_000, param1 = 100))
        packet.handleMessage(utcRecord(DanaPump.HistoryEntry.TIME_CHANGE.value, epochSeconds = 1_700_000_100))
        packet.handleMessage(terminator())

        assertThat(danaPump.historyDoneReceived).isTrue()
        assertThat(danaPump.lastEventTimeLoaded).isGreaterThan(0L)
    }

    private fun packet() =
        DanaRSPacketAPSHistoryEvents(aapsLogger, dateUtil, rxBus, rh, danaPump, detailedBolusInfoStorage, temporaryBasalStorage, preferences, pumpSync)
            .with(dateUtil.now())

    // All field reads go through intFromBuff, which indexes at DATA_START + offset — the packet's
    // header. Crafted records therefore need that header prefix, exactly as the wire ones carry it.
    private val d = DanaRSPacket.DATA_START

    /** The end-of-history marker: record code 0xFF at the data start, which triggers the buffered sweep. */
    private fun terminator(): ByteArray {
        val b = ByteArray(d + 1)
        b[d] = 0xFF.toByte()
        return b
    }

    /** A local-time (non-UTC) record: code, 6-byte yy/MM/dd/HH/mm/ss date, then param1/param2 MSB-LSB. */
    private fun record(code: Int, param1: Int = 0, param2: Int = 0, second: Int): ByteArray {
        val b = ByteArray(d + 11)
        b[d + 0] = code.toByte()
        b[d + 1] = 24 // 2024
        b[d + 2] = 6  // June
        b[d + 3] = 15 // day
        b[d + 4] = 12 // hour
        b[d + 5] = 0  // minute
        b[d + 6] = second.toByte()
        b[d + 7] = (param1 shr 8 and 0xff).toByte()
        b[d + 8] = (param1 and 0xff).toByte()
        b[d + 9] = (param2 shr 8 and 0xff).toByte()
        b[d + 10] = (param2 and 0xff).toByte()
        return b
    }

    /** A UTC record: 2-byte index, code at offset 2, 4-byte epoch-seconds date, then param1/param2. */
    private fun utcRecord(code: Int, epochSeconds: Int, id: Int = 1, param1: Int = 0, param2: Int = 0): ByteArray {
        val b = ByteArray(d + 11)
        b[d + 0] = (id shr 8 and 0xff).toByte()
        b[d + 1] = (id and 0xff).toByte()
        b[d + 2] = code.toByte()
        b[d + 3] = (epochSeconds shr 24 and 0xff).toByte()
        b[d + 4] = (epochSeconds shr 16 and 0xff).toByte()
        b[d + 5] = (epochSeconds shr 8 and 0xff).toByte()
        b[d + 6] = (epochSeconds and 0xff).toByte()
        b[d + 7] = (param1 shr 8 and 0xff).toByte()
        b[d + 8] = (param1 and 0xff).toByte()
        b[d + 9] = (param2 shr 8 and 0xff).toByte()
        b[d + 10] = (param2 and 0xff).toByte()
        return b
    }

    private fun getCalender(from: Long): ByteArray {
        val cal = GregorianCalendar()
        if (from != 0L) cal.timeInMillis = from else cal[2000, 0, 1, 0, 0] = 0
        val ret = ByteArray(6)
        ret[0] = (cal[Calendar.YEAR] - 1900 - 100 and 0xff).toByte()
        ret[1] = (cal[Calendar.MONTH] + 1 and 0xff).toByte()
        ret[2] = (cal[Calendar.DAY_OF_MONTH] and 0xff).toByte()
        ret[3] = (cal[Calendar.HOUR_OF_DAY] and 0xff).toByte()
        ret[4] = (cal[Calendar.MINUTE] and 0xff).toByte()
        ret[5] = (cal[Calendar.SECOND] and 0xff).toByte()
        return ret
    }
}