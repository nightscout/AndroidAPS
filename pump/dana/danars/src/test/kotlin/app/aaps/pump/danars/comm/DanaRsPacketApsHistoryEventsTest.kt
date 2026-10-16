package app.aaps.pump.danars.comm

import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.pump.DetailedBolusInfo
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
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

    // ---- Dana-i2 STEP_BOLUS_COMMAND (18) ---------------------------------------------------------

    private val bolusTime = 1_790_870_000 // epoch seconds

    /** Feeds a STEP_BOLUS_COMMAND and a BOLUS record (Dana-i2 UTC format) and ends the history. */
    private fun feedCommandAndBolus(commandType: Int, commandOffsetSec: Int = 0, commandAmount: Int = 50, delivered: Int = 50) {
        danaPump.hwModel = 0x0B // Dana-i2 → usingUTC = true
        val packet = packet()
        // The pump sends the newest record first
        packet.handleMessage(utcRecord(DanaPump.HistoryEntry.BOLUS.value, epochSeconds = bolusTime, id = 2, param1 = delivered))
        packet.handleMessage(utcRecord(DanaPump.HistoryEntry.STEP_BOLUS_COMMAND.value, epochSeconds = bolusTime + commandOffsetSec, id = 1, param1 = commandAmount, param2 = commandType))
        packet.handleMessage(terminator())
        assertThat(danaPump.historyDoneReceived).isTrue()
    }

    @Test fun algorithmStepBolusCommandMarksTheBolusAsSmb() {
        feedCommandAndBolus(commandType = 1)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), eq(BS.Type.SMB), any(), any(), any()) }
    }

    @Test fun normalStepBolusCommandMarksTheBolusAsNormal() {
        feedCommandAndBolus(commandType = 0)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), eq(BS.Type.NORMAL), any(), any(), any()) }
    }

    @Test fun bolusStoppedEarlyStillMatchesItsCommand() {
        // 0.5 U was set, the user stopped it after 0.2 U
        feedCommandAndBolus(commandType = 1, commandAmount = 50, delivered = 20)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), eq(BS.Type.SMB), any(), any(), any()) }
    }

    @Test fun slowLargeBolusMatchesItsCommandAtTheStart() {
        // BOLUS has the end time: 10 U at 60 s/U ended 600 s after the command
        feedCommandAndBolus(commandType = 1, commandOffsetSec = -600, commandAmount = 1000, delivered = 1000)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), eq(BS.Type.SMB), any(), any(), any()) }
    }

    @Test fun commandLongerBeforeThanTheSlowestDeliveryIsNotUsed() {
        // 0.5 U takes at most 30 s, plus 60 s margin
        feedCommandAndBolus(commandType = 1, commandOffsetSec = -91)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), isNull(), any(), any(), any()) }
    }

    @Test fun commandAfterTheBolusIsNotUsed() {
        feedCommandAndBolus(commandType = 1, commandOffsetSec = 61)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), isNull(), any(), any(), any()) }
    }

    @Test fun commandForSmallerAmountIsNotUsed() {
        // A 0.2 U command cannot be the source of a 0.5 U bolus
        feedCommandAndBolus(commandType = 1, commandAmount = 20, delivered = 50)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), isNull(), any(), any(), any()) }
    }

    @Test fun storedBolusInfoWinsOverTheCommand() {
        val stored = DetailedBolusInfo().also { it.bolusType = BS.Type.NORMAL }
        whenever(detailedBolusInfoStorage.findDetailedBolusInfo(any(), any())).thenReturn(stored)

        feedCommandAndBolus(commandType = 1)

        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(bolusTime * 1000L), any(), eq(BS.Type.NORMAL), any(), any(), any()) }
    }

    // ---- Dana-i2 BASAL_RATE_CHANGE (19) ---------------------------------------------------------
    // Records taken from a real Dana-i2 (CFL00001TJ) history, without the B2 C2 header

    private fun realRecord(vararg bytes: Int): ByteArray =
        ByteArray(d) + bytes.map { it.toByte() }.toByteArray()

    private fun feed(vararg records: ByteArray) {
        danaPump.hwModel = 0x0B
        val packet = packet()
        records.forEach { packet.handleMessage(it) }
        packet.handleMessage(terminator())
        assertThat(danaPump.historyDoneReceived).isTrue()
    }

    @Test fun basalRateChangeIsOnlyLogged() {
        // Not known yet which reasons AAPS causes itself, so nothing is synced
        feed(
            realRecord(0x00, 0x05, 0x13, 0x6A, 0xBD, 0x78, 0x73, 0x00, 0x14, 0x00, 0x01), // reason 1 Power On
            realRecord(0x00, 0x01, 0x13, 0x5A, 0x49, 0x7A, 0x00, 0x00, 0x14, 0x00, 0x07)  // reason 7 Basal Setting Change
        )

        verifyNoInteractions(pumpSync)
    }

    @Test fun tempBasalReasonsOnlyRepeatOtherRecords() {
        // Real loop sequence: TEMP_START 0 % 120 min + reason 4, then TEMP_STOP + reason 5
        feed(
            realRecord(0x00, 0x79, 0x13, 0x6A, 0xBE, 0xB6, 0xF4, 0x00, 0x00, 0x00, 0x04),
            realRecord(0x00, 0x78, 0x01, 0x6A, 0xBE, 0xB6, 0xF4, 0x00, 0x00, 0x00, 0x78),
            realRecord(0x00, 0x77, 0x13, 0x6A, 0xBE, 0xB6, 0xF3, 0x00, 0x14, 0x00, 0x05),
            realRecord(0x00, 0x76, 0x02, 0x6A, 0xBE, 0xB6, 0xF3, 0x00, 0x00, 0x00, 0x00)
        )

        // The temp basal comes only from events 1 and 2
        verifyBlocking(pumpSync) { syncTemporaryBasalWithPumpId(eq(0x6ABEB6F4L * 1000), any(), eq(T.mins(120).msecs()), eq(false), anyOrNull(), any(), any(), any()) }
        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(eq(0x6ABEB6F3L * 1000), any(), any(), any(), any()) }
        verifyNoMoreInteractions(pumpSync)
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