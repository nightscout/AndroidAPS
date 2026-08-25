package app.aaps.plugins.calibration

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LinearCalibrationPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider

    private lateinit var plugin: LinearCalibrationPlugin

    private val now: Long = 1_700_000_000_000L

    /** Default sessionStart for tests that don't override — 12h ago, past warm-up. */
    private val defaultSessionStart: Long get() = now - T.hours(12).msecs()

    @BeforeEach
    fun setUp() = runTest {
        whenever(dateUtil.now()).thenReturn(now)
        whenever(dateUtil.timeString(any())).thenReturn("12:00")
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(defaultSessionStart))
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(emptyList())
        plugin = LinearCalibrationPlugin(
            aapsLogger, rh, dateUtil, persistenceLayer, notificationManager, glucoseStatusProvider, rxBus
        )
    }

    // ------------ calibrate() ------------

    @Test
    fun calibrate_emptyData_returnsEmpty() = runTest {
        val data = mutableListOf<InMemoryGlucoseValue>()
        val result = plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(result).isEmpty()
    }

    @Test
    fun calibrate_inWarmUp_returnsIdentity() = runTest {
        // Session started 1h ago — within 2h warm-up
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(now - T.hours(1).msecs()))
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(twoGoodEntries())
        val data = bucketed(timestamps(now, every = 5))
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data.all { it.calibrated == null }).isTrue()
    }

    @Test
    fun calibrate_noSessionStart_returnsIdentity() = runTest {
        // No SENSOR_CHANGE recorded — calibration must NOT blend across sensor sessions
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(null)
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(twoGoodEntries())
        val data = bucketed(listOf(now to 150.0))
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data[0].calibrated).isNull()
    }

    @Test
    fun calibrate_fewerThanTwoEntries_returnsIdentity() = runTest {
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(listOf(entry(sensor = 100.0, fs = 110.0, ageDays = 1L)))
        val data = bucketed(timestamps(now, every = 5))
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data.all { it.calibrated == null }).isTrue()
    }

    @Test
    fun calibrate_validFit_appliesSlopeAndOffset() = runTest {
        // Two entries on the line y = 1.1 * x + 0
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(
            listOf(
                entry(sensor = 100.0, fs = 110.0, ageDays = 1L),
                entry(sensor = 200.0, fs = 220.0, ageDays = 1L)
            )
        )
        val data = bucketed(listOf(now to 150.0))
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data[0].calibrated!!).isWithin(0.01).of(165.0)
    }

    @Test
    fun calibrate_pureOffset_appliesCorrectly() = runTest {
        // Sensor reads 10 mg/dL too low across the range -> slope=1, offset=10
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(
            listOf(
                entry(sensor = 100.0, fs = 110.0, ageDays = 1L),
                entry(sensor = 200.0, fs = 210.0, ageDays = 1L)
            )
        )
        val data = bucketed(listOf(now to 150.0))
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data[0].calibrated!!).isWithin(0.01).of(160.0)
    }

    @Test
    fun calibrate_slopeOutOfRange_returnsIdentity() = runTest {
        // y = 2x — slope 2.0 is way outside [0.6, 1.4]
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(
            listOf(
                entry(sensor = 100.0, fs = 200.0, ageDays = 1L),
                entry(sensor = 200.0, fs = 400.0, ageDays = 1L)
            )
        )
        val data = bucketed(listOf(now to 150.0))
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data[0].calibrated).isNull()
    }

    @Test
    fun calibrate_clusteredEntries_appliesOffsetOnly() = runTest {
        // Five entries all within ~6 mg/dL of each other — slope estimate would be noise.
        // Mean delta (FS - sensor) = (3 + 5 + 4 + 6 + 2) / 5 = 4 mg/dL → expected correction.
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(
            listOf(
                entry(sensor = 140.0, fs = 143.0, ageDays = 0L),
                entry(sensor = 141.0, fs = 146.0, ageDays = 0L),
                entry(sensor = 142.0, fs = 146.0, ageDays = 0L),
                entry(sensor = 143.0, fs = 149.0, ageDays = 0L),
                entry(sensor = 144.0, fs = 146.0, ageDays = 0L)
            )
        )
        val data = bucketed(listOf(now to 80.0, now - T.mins(5).msecs() to 250.0))
        plugin.calibrate(data, CalibrationContext.NONE)
        // Slope locked to 1.0, so correction is constant ~4 mg/dL across the whole range.
        assertThat(data[0].calibrated!!).isWithin(0.5).of(84.0)
        assertThat(data[1].calibrated!!).isWithin(0.5).of(254.0)
    }

    @Test
    fun calibrate_offsetOutOfRange_returnsIdentity() = runTest {
        // y = x + 50 — offset 50 is outside [-30, +30]
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(
            listOf(
                entry(sensor = 100.0, fs = 150.0, ageDays = 1L),
                entry(sensor = 200.0, fs = 250.0, ageDays = 1L)
            )
        )
        val data = bucketed(listOf(now to 150.0))
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data[0].calibrated).isNull()
    }

    @Test
    fun calibrate_onlyAppliesAfterSessionStart() = runTest {
        // Session start 6h ago. Old (8h ago) point should NOT be calibrated.
        val sessionStart = now - T.hours(6).msecs()
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(sessionStart))
        whenever(persistenceLayer.getValidCalibrationEntriesSince(eq(sessionStart))).thenReturn(twoGoodEntries())
        val data = bucketed(
            listOf(
                now to 150.0,
                now - T.hours(5).msecs() to 150.0,
                now - T.hours(8).msecs() to 150.0
            )
        )
        plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(data[0].calibrated).isNotNull()
        assertThat(data[1].calibrated).isNotNull()
        assertThat(data[2].calibrated).isNull() // older than session start
    }

    @Test
    fun calibrate_gapDetected_postsNotification() = runTest {
        whenever(rh.gs(any<Int>(), any())).thenReturn("Possible sensor change")
        // Gap of 60min between data[0] and data[1]; default sessionStart is 12h ago so gap is within session
        val data = mutableListOf(
            value(now, 150.0),
            value(now - T.mins(60).msecs(), 150.0),
            value(now - T.mins(65).msecs(), 150.0)
        )
        plugin.calibrate(data, CalibrationContext.NONE)
        verify(notificationManager).post(
            eq(NotificationId.SENSOR_CHANGE_DETECTED),
            any<String>(),
            any<NotificationLevel>(),
            any<Int>(),
            anyOrNull(),
            any<List<NotificationAction>>(),
            anyOrNull()
        )
    }

    @Test
    fun calibrate_gapWithNearbySensorChange_skipsNotification() = runTest {
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(
            listOf(sensorChange(now - T.mins(35).msecs()))
        )
        val data = mutableListOf(
            value(now, 150.0),
            value(now - T.mins(60).msecs(), 150.0),
            value(now - T.mins(65).msecs(), 150.0)
        )
        plugin.calibrate(data, CalibrationContext.NONE)
        verify(notificationManager, never()).post(
            any<NotificationId>(),
            any<String>(),
            any<NotificationLevel>(),
            any<Int>(),
            anyOrNull(),
            any<List<NotificationAction>>(),
            anyOrNull()
        )
    }

    @Test
    fun calibrate_notificationAction_insertsSensorChange() = runTest {
        whenever(rh.gs(any<Int>(), any())).thenReturn("Possible sensor change")
        whenever(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), any(), any()))
            .thenReturn(PersistenceLayer.TransactionResult())
        val data = mutableListOf(
            value(now, 150.0),
            value(now - T.mins(60).msecs(), 150.0),
            value(now - T.mins(65).msecs(), 150.0)
        )
        plugin.calibrate(data, CalibrationContext.NONE)

        val actionsCaptor = argumentCaptor<List<NotificationAction>>()
        verify(notificationManager).post(
            any<NotificationId>(),
            any<String>(),
            any<NotificationLevel>(),
            any<Int>(),
            anyOrNull(),
            actionsCaptor.capture(),
            anyOrNull()
        )
        actionsCaptor.firstValue.single().action.invoke()

        verify(persistenceLayer).insertPumpTherapyEventIfNewByTimestamp(
            any(), any(), any(), any(), anyOrNull(), any()
        )
    }

    // ------------ addEntry() ------------

    @Test
    fun addEntry_calmDelta_inserts() = runTest {
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 0.5))
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), eq(false)))
            .thenReturn(listOf(bgReading(now, 145.0)))
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isEqualTo(AddEntryResult.Accepted)
        verify(persistenceLayer).insertOrUpdateCalibrationEntry(eq(CAL(timestamp = now, fingerstickMgdl = 150.0, sensorMgdlAtPairing = 145.0)))
    }

    @Test
    fun addEntry_nullGlucoseStatus_inserts() = runTest {
        // No glucose status available -> delta gate falls through, insert proceeds
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(null)
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), eq(false)))
            .thenReturn(listOf(bgReading(now, 145.0)))
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isEqualTo(AddEntryResult.Accepted)
        verify(persistenceLayer).insertOrUpdateCalibrationEntry(eq(CAL(timestamp = now, fingerstickMgdl = 150.0, sensorMgdlAtPairing = 145.0)))
    }

    @Test
    fun addEntry_highDelta_rejectsDeltaTooHigh() = runTest {
        // shortAvgDelta is in mg/dL per 5 min; threshold is 5.0 with no fit (slope=1 effective).
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 6.0))
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isInstanceOf(AddEntryResult.Rejected.DeltaTooHigh::class.java)
        assertThat((result as AddEntryResult.Rejected.DeltaTooHigh).deltaMgdlPer5Min).isWithin(0.01).of(6.0)
        assertThat(result.thresholdMgdlPer5Min).isWithin(0.01).of(5.0)
        verify(persistenceLayer, never()).insertOrUpdateCalibrationEntry(any())
    }

    @Test
    fun addEntry_deltaThresholdScaledBySlopeWhenFitApplicable() = runTest {
        // Two entries imply slope = 1.05, well inside clamps → fit is applicable.
        // Effective threshold becomes 5.0 * 1.05 = 5.25 mg/dL/5min.
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(twoGoodEntries())
        // Delta 5.2: would be rejected without scaling, accepted with slope-scaled threshold.
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 5.2))
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), eq(false)))
            .thenReturn(listOf(bgReading(now, 145.0)))
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isEqualTo(AddEntryResult.Accepted)
    }

    @Test
    fun addEntry_deltaExceedsScaledThreshold_rejectsWithScaledThreshold() = runTest {
        // Same fit (slope=1.05), but delta 6.0 still exceeds the scaled threshold 5.25.
        whenever(persistenceLayer.getValidCalibrationEntriesSince(any())).thenReturn(twoGoodEntries())
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 6.0))
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isInstanceOf(AddEntryResult.Rejected.DeltaTooHigh::class.java)
        // The carried threshold should be the scaled value, not the raw base.
        assertThat((result as AddEntryResult.Rejected.DeltaTooHigh).thresholdMgdlPer5Min).isWithin(0.01).of(5.25)
    }

    @Test
    fun addEntry_noNearbyReading_rejectsNoSensorPair() = runTest {
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 0.5))
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), eq(false)))
            .thenReturn(emptyList())
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isEqualTo(AddEntryResult.Rejected.NoSensorPair)
        verify(persistenceLayer, never()).insertOrUpdateCalibrationEntry(any())
    }

    @Test
    fun addEntry_inWarmUp_rejectsInWarmUp() = runTest {
        // Session started 1h ago — inside 2h warm-up window
        val sessionStart = now - T.hours(1).msecs()
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(sessionStart))
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 0.5))
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isInstanceOf(AddEntryResult.Rejected.InWarmUp::class.java)
        assertThat((result as AddEntryResult.Rejected.InWarmUp).warmUpEndsAt).isEqualTo(sessionStart + T.hours(2).msecs())
        verify(persistenceLayer, never()).insertOrUpdateCalibrationEntry(any())
    }

    @Test
    fun addEntry_noSession_rejectsNoSession() = runTest {
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(null)
        val result = plugin.addEntry(bgMgdl = 150.0, timestamp = now)
        assertThat(result).isEqualTo(AddEntryResult.Rejected.NoSession)
        verify(persistenceLayer, never()).insertOrUpdateCalibrationEntry(any())
    }

    // ------------ checkPreconditions() ------------

    @Test
    fun checkPreconditions_noSession_returnsNoSession() = runTest {
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(null)
        assertThat(plugin.checkPreconditions()).isEqualTo(AddEntryResult.Rejected.NoSession)
    }

    @Test
    fun checkPreconditions_inWarmUp_returnsInWarmUpWithEndsAt() = runTest {
        val sessionStart = now - T.hours(1).msecs()
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)).thenReturn(sensorChange(sessionStart))
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 0.5))
        val result = plugin.checkPreconditions()
        assertThat(result).isInstanceOf(AddEntryResult.Rejected.InWarmUp::class.java)
        assertThat((result as AddEntryResult.Rejected.InWarmUp).warmUpEndsAt).isEqualTo(sessionStart + T.hours(2).msecs())
    }

    @Test
    fun checkPreconditions_highDelta_returnsDeltaTooHigh() = runTest {
        // shortAvgDelta is mg/dL per 5 min; 6.0 is above the 5.0 threshold.
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 6.0))
        val result = plugin.checkPreconditions()
        assertThat(result).isInstanceOf(AddEntryResult.Rejected.DeltaTooHigh::class.java)
    }

    @Test
    fun checkPreconditions_noNearbyReading_returnsNoSensorPair() = runTest {
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 0.5))
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), eq(false)))
            .thenReturn(emptyList())
        assertThat(plugin.checkPreconditions()).isEqualTo(AddEntryResult.Rejected.NoSensorPair)
    }

    @Test
    fun checkPreconditions_allClear_returnsAccepted() = runTest {
        whenever(glucoseStatusProvider.glucoseStatusData).thenReturn(glucoseStatus(shortAvgDelta = 0.5))
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), eq(false)))
            .thenReturn(listOf(bgReading(now, 145.0)))
        assertThat(plugin.checkPreconditions()).isEqualTo(AddEntryResult.Accepted)
    }

    // ------------ helpers ------------

    private fun value(ts: Long, v: Double) = InMemoryGlucoseValue(
        timestamp = ts,
        value = v,
        trendArrow = TrendArrow.NONE,
        sourceSensor = SourceSensor.UNKNOWN
    )

    /** Builds a bucketed list, newest first, evenly spaced 5 minutes apart. */
    private fun bucketed(pairs: List<Pair<Long, Double>>) =
        pairs.map { (ts, v) -> value(ts, v) }.toMutableList()

    private fun timestamps(start: Long, every: Long, count: Int = 6): List<Pair<Long, Double>> =
        (0 until count).map { i -> (start - T.mins(every * i).msecs()) to 150.0 }

    private fun entry(sensor: Double, fs: Double, ageDays: Long): CAL =
        CAL(
            id = 0L,
            timestamp = now - T.days(ageDays).msecs(),
            fingerstickMgdl = fs,
            sensorMgdlAtPairing = sensor
        )

    private fun twoGoodEntries() = listOf(
        entry(sensor = 100.0, fs = 105.0, ageDays = 1L),
        entry(sensor = 200.0, fs = 210.0, ageDays = 1L)
    )

    private fun sensorChange(timestamp: Long): TE =
        TE(timestamp = timestamp, type = TE.Type.SENSOR_CHANGE, glucoseUnit = GlucoseUnit.MGDL)

    private fun glucoseStatus(shortAvgDelta: Double): GlucoseStatusSMB =
        GlucoseStatusSMB(glucose = 150.0, shortAvgDelta = shortAvgDelta, date = now)

    private fun bgReading(timestamp: Long, value: Double): GV = GV(
        timestamp = timestamp,
        value = value,
        raw = null,
        noise = null,
        trendArrow = TrendArrow.NONE,
        sourceSensor = SourceSensor.UNKNOWN
    )
}
