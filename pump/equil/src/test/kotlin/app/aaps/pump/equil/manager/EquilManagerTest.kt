package app.aaps.pump.equil.manager

import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.EquilBLE
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.events.EventEquilAlarm
import app.aaps.pump.equil.events.EventEquilResumed
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.Calendar
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EquilManagerTest : TestBaseWithProfile() {

    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var equilBLE: EquilBLE
    @Mock private lateinit var equilHistoryRecordDao: EquilHistoryRecordDao
    @Mock private lateinit var equilHistoryPumpDao: EquilHistoryPumpDao
    @Mock lateinit var bolusProgressData: BolusProgressData

    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(rh.gs(R.string.equil_history_item3)).thenReturn("Error 1")
        whenever(rh.gs(R.string.equil_history_item4)).thenReturn("Error 2")
        whenever(rh.gs(R.string.equil_history_item5)).thenReturn("Error 3")
        whenever(rh.gs(R.string.equil_shutdown_be)).thenReturn("Shutdown BE")
        whenever(rh.gs(R.string.equil_shutdown)).thenReturn("Shutdown")
        whenever(rh.gs(R.string.equil_history_item18)).thenReturn("Error 6")

        equilManager = EquilManager(
            aapsLogger,
            rxBus,
            preferences,
            rh,
            pumpSync,
            equilBLE,
            equilHistoryRecordDao,
            equilHistoryPumpDao,
            pumpEnactResultProvider,
            dateUtil,
            ch,
            bolusProgressData,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    // --- pump suspend keeps IOB right (#3830) ---

    private fun prepareRunMode(mode: RunMode, runningTbrType: PumpSync.TemporaryBasalType? = null) {
        equilManager.clearPodState()
        equilManager.setSerialNumber("7000F3")
        equilManager.equilState?.runMode = mode
        val tbr = runningTbrType?.let { PumpSync.PumpState.TemporaryBasal(1000L, 60_000L, 0.0, true, it, 1L, 1L) }
        runBlocking { whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(tbr, null, null, null, "7000F3")) }
    }

    @Test
    fun `suspend records a 0 U per hour PUMP_SUSPEND temp basal`() = runTest {
        prepareRunMode(RunMode.RUN, runningTbrType = PumpSync.TemporaryBasalType.NORMAL)

        equilManager.setRunMode(RunMode.SUSPEND)

        verify(pumpSync).syncTemporaryBasalWithPumpId(
            any(), argThat<PumpRate> { cU == 0.0 }, any(), eq(true), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), any(), eq(PumpType.EQUIL), eq("7000F3")
        )
    }

    @Test
    fun `stop records a PUMP_SUSPEND temp basal too`() = runTest {
        prepareRunMode(RunMode.RUN)

        equilManager.setRunMode(RunMode.STOP)

        verify(pumpSync).syncTemporaryBasalWithPumpId(
            any(), any(), any(), any(), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), any(), any(), any()
        )
    }

    @Test
    fun `resume ends the PUMP_SUSPEND temp basal`() = runTest {
        prepareRunMode(RunMode.SUSPEND, runningTbrType = PumpSync.TemporaryBasalType.PUMP_SUSPEND)

        equilManager.setRunMode(RunMode.RUN)

        verify(pumpSync).syncStopTemporaryBasalWithPumpId(any(), any(), eq(PumpType.EQUIL), eq("7000F3"), any())
    }

    @Test
    fun `resume sends EventEquilResumed, so the old pump temp basal is cancelled`() = runTest {
        prepareRunMode(RunMode.SUSPEND)
        val events = mutableListOf<EventEquilResumed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rxBus.toFlow(EventEquilResumed::class).toList(events) }

        equilManager.setRunMode(RunMode.RUN)

        assertEquals(1, events.size)
    }

    @Test
    fun `suspend and a repeated RUN do not send EventEquilResumed`() = runTest {
        prepareRunMode(RunMode.RUN)
        val events = mutableListOf<EventEquilResumed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rxBus.toFlow(EventEquilResumed::class).toList(events) }

        equilManager.setRunMode(RunMode.RUN)
        equilManager.setRunMode(RunMode.SUSPEND)

        assertEquals(0, events.size)
    }

    @Test
    fun `resume does not end a temp basal set by the loop`() = runTest {
        prepareRunMode(RunMode.SUSPEND, runningTbrType = PumpSync.TemporaryBasalType.NORMAL)

        equilManager.setRunMode(RunMode.RUN)

        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
    }

    @Test
    fun `the same mode read again records nothing`() = runTest {
        prepareRunMode(RunMode.SUSPEND)

        // The running mode is read from the pump on every connection.
        equilManager.setRunMode(RunMode.SUSPEND)
        equilManager.setRunMode(RunMode.STOP)

        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a running PUMP_SUSPEND is not written twice`() = runTest {
        prepareRunMode(RunMode.RUN, runningTbrType = PumpSync.TemporaryBasalType.PUMP_SUSPEND)

        equilManager.setRunMode(RunMode.SUSPEND)

        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any())
    }

    // --- pump history: alarms (#5077) and battery empty gap (#3830) ---

    /** One pump history record as CmdHistoryGet passes it to decodeHistory. [time] in whole seconds. */
    private fun historyRecord(index: Int, time: Long, port: Int, type: Int, level: Int, rate: Int = 0): ByteArray {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val d = ByteArray(24)
        d[6] = (c.get(Calendar.YEAR) - 2000).toByte()
        d[7] = (c.get(Calendar.MONTH) + 1).toByte()
        d[8] = c.get(Calendar.DAY_OF_MONTH).toByte()
        d[9] = c.get(Calendar.HOUR_OF_DAY).toByte()
        d[10] = c.get(Calendar.MINUTE).toByte()
        d[11] = c.get(Calendar.SECOND).toByte()
        d[14] = (rate and 0xff).toByte()
        d[15] = (rate shr 8).toByte()
        d[18] = (index and 0xff).toByte()
        d[19] = (index shr 8).toByte()
        d[20] = port.toByte()
        d[21] = type.toByte()
        d[22] = level.toByte()
        return d
    }

    private val minute = 60_000L

    private fun TestScope.collectAlarms(): MutableList<EventEquilAlarm> {
        val alarms = mutableListOf<EventEquilAlarm>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rxBus.toFlow(EventEquilAlarm::class).toList(alarms) }
        return alarms
    }

    private fun prepareHistory() {
        prepareRunMode(RunMode.RUN)
        whenever(rh.gs(R.string.equil_history_item3)).thenReturn("Blockage")
    }

    @Test
    fun `a new blockage record alarms once with sound`() = runTest {
        prepareHistory()
        val alarms = collectAlarms()

        equilManager.decodeHistory(historyRecord(1700, now - 2 * minute, 4, 2, 2))

        assertEquals(listOf(EventEquilAlarm("Blockage", sound = true)), alarms)
        verify(equilHistoryRecordDao).insert(argThat<EquilHistoryRecord> { type == EquilHistoryRecord.EventType.EQUIL_ALARM && note == "Blockage" })
    }

    @Test
    fun `a blockage read 2 hours late is shown without sound`() = runTest {
        prepareHistory()
        val alarms = collectAlarms()

        equilManager.decodeHistory(historyRecord(1700, now - 120 * minute, 4, 2, 2))

        assertEquals(listOf(EventEquilAlarm("Blockage", sound = false)), alarms)
    }

    @Test
    fun `a reservoir change with battery swap raises no alarm and no gap (real records 1690-1696)`() = runTest {
        prepareHistory()
        val alarms = collectAlarms()
        val stop = now - 10 * minute
        val powerOn = stop + 4 * minute

        listOf(
            historyRecord(1690, stop, 4, 11, 0),           // TBR end
            historyRecord(1691, stop, 4, 0, 0),            // status
            historyRecord(1692, stop, 4, 8, 0),            // rewind
            historyRecord(1693, powerOn, 5, 1, 0),         // power-on reset, new battery
            historyRecord(1694, powerOn, 4, 8, 0),         // rewind
            historyRecord(1695, powerOn, 4, 7, 0),         // prime
            historyRecord(1696, powerOn + 5000, 4, 0, 0, rate = 192)
        ).forEach { equilManager.decodeHistory(it) }

        // The rewinds used to raise "auto off" twice per reservoir change.
        assertEquals(emptyList<EventEquilAlarm>(), alarms)
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any())
        // Nothing for the careportal either: a reservoir change is recorded by the wizard.
        verify(pumpSync, never()).insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `a dead battery gets a 0 U per hour gap until delivery starts again (real records 1561-1568)`() = runTest {
        prepareHistory()
        whenever(rh.gs(R.string.equil_history_item16)).thenReturn("Battery empty!")
        val alarms = collectAlarms()
        val empty = now - 300 * minute
        val powerOn = now - 10 * minute
        val delivering = powerOn + 5000

        listOf(
            historyRecord(1561, empty, 5, 0, 2),           // battery empty: delivery stops
            historyRecord(1562, empty, 4, 11, 0),          // TBR end
            historyRecord(1563, empty, 4, 0, 0),           // status, rate 0
            historyRecord(1564, empty, 4, 8, 0),           // rewind
            historyRecord(1565, powerOn, 5, 1, 0),         // power-on reset
            historyRecord(1566, powerOn, 4, 8, 0),         // rewind
            historyRecord(1567, powerOn, 4, 7, 0),         // prime
            historyRecord(1568, delivering, 4, 0, 0, rate = 212)
        ).forEach { equilManager.decodeHistory(it) }

        // First opened like a suspend, then given its real length. Same pump id, so one record.
        val durations = argumentCaptor<Long>()
        verify(pumpSync, times(2)).syncTemporaryBasalWithPumpId(
            eq(empty), argThat<PumpRate> { cU == 0.0 }, durations.capture(), eq(true), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), eq(empty), eq(PumpType.EQUIL), eq("7000F3")
        )
        assertEquals(listOf(4800 * minute, delivering - empty), durations.allValues)
        assertEquals(null, equilManager.equilState?.deliveryStoppedAt)
        assertEquals(emptyList<EventEquilAlarm>(), alarms)
        // No alarm (the pump was dead), but the careportal shows when and why it stopped (#4132).
        verify(pumpSync).insertTherapyEventIfNewWithTimestamp(eq(empty), eq(TE.Type.ANNOUNCEMENT), eq("Battery empty!"), anyOrNull(), eq(PumpType.EQUIL), eq("7000F3"))
        verify(pumpSync, times(1)).insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `an open battery gap ends at the next RUN`() = runTest {
        prepareHistory()
        val empty = now - 60 * minute
        // Read while the pump is stopped after the new battery; no record with delivery yet.
        equilManager.setRunMode(RunMode.STOP)
        equilManager.decodeHistory(historyRecord(1561, empty, 5, 0, 2))

        equilManager.setRunMode(RunMode.RUN)

        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(empty), any(), eq(now - empty), any(), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), eq(empty), any(), any()
        )
        assertEquals(null, equilManager.equilState?.deliveryStoppedAt)
    }

    // --- reservoir empty during a bolus (#3467), real records 1711-1717 of 2026-10-11 ---

    private val bolusStart = now - 20 * minute
    private val empty = bolusStart + 477_000 // 18:14:43 -> 18:22:40: the reservoir ran empty
    private val bolusEnd = empty + 7_000     // the progress timer reached 100 % 7 s later

    private fun readEmptyReservoirHistory() {
        listOf(
            historyRecord(1711, bolusStart, 4, 0, 0, rate = 320),
            historyRecord(1712, bolusStart + 87_000, 4, 1, 1, rate = 320),  // reservoir low
            historyRecord(1713, empty, 4, 11, 0, rate = 320),              // TBR end
            historyRecord(1714, empty, 4, 1, 2),                           // reservoir empty
            historyRecord(1715, empty, 4, 8, 0)                            // rewind
        ).forEach { equilManager.decodeHistory(it) }
    }

    @Test
    fun `an empty reservoir alarms and opens a 0 U per hour gap`() = runTest {
        prepareHistory()
        whenever(rh.gs(R.string.equil_history_item2)).thenReturn("Reservoir empty")
        val alarms = collectAlarms()

        readEmptyReservoirHistory()

        assertEquals(listOf("Reservoir empty"), alarms.map { it.tips })
        // The pump stops basal too: no delivery until the refill.
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(empty), argThat<PumpRate> { cU == 0.0 }, eq(4800 * minute), eq(true), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), eq(empty), eq(PumpType.EQUIL), eq("7000F3")
        )
        assertEquals(empty, equilManager.equilState?.deliveryStoppedAt)
        // In the careportal at the pump's time; "reservoir low" (4, 1, 1) is not recorded.
        verify(pumpSync).insertTherapyEventIfNewWithTimestamp(eq(empty), eq(TE.Type.ANNOUNCEMENT), eq("Reservoir empty"), anyOrNull(), eq(PumpType.EQUIL), eq("7000F3"))
        verify(pumpSync, times(1)).insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `the gap ends where an already recorded suspend starts`() = runTest {
        prepareHistory()
        // The run mode read right after the bolus saw STOP 10 s after the empty moment and recorded a
        // suspend from then (seen on the pump on 2026-10-11: 18:57:49 empty, 18:57:59 suspend).
        val suspend = PumpSync.PumpState.TemporaryBasal(empty + 10_000, 4800 * minute, 0.0, true, PumpSync.TemporaryBasalType.PUMP_SUSPEND, 1L, 1L)
        runBlocking { whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(suspend, null, null, null, "7000F3")) }

        readEmptyReservoirHistory()

        // Only the 10 s before it, no second open record on top of it.
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(empty), any(), eq(10_000L), any(), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), eq(empty), any(), any()
        )
        assertEquals(null, equilManager.equilState?.deliveryStoppedAt)
    }

    @Test
    fun `a bolus that ran into an empty reservoir records only what was given`() = runTest {
        prepareHistory()
        whenever(rh.gs(R.string.equil_history_item2)).thenReturn("Reservoir empty")
        whenever(rh.gs(eq(R.string.equil_bolus_stopped), anyOrNull(), anyOrNull())).thenReturn("Bolus stopped")
        val alarms = collectAlarms()
        val bolus = DetailedBolusInfo().apply { insulin = 12.0; timestamp = bolusStart - 3000 }
        val result = pumpEnactResultProvider().success(true).enacted(true).bolusDelivered(12.0)
        readEmptyReservoirHistory() // read right after the bolus

        equilManager.correctBolusIfDeliveryStopped(result, bolus, bolusStart, bolusEnd, 12.0 / 480)

        // 477 s x 1.5 U/min = 11.925 U, rounded up to the 0.05 U step.
        assertEquals(11.95, result.bolusDelivered, 0.0001)
        verify(pumpSync).syncBolusWithPumpId(
            eq(bolusEnd), argThat<PumpInsulin> { abs(cU - 11.95) < 0.0001 }, anyOrNull(), eq(bolus.timestamp), eq(PumpType.EQUIL), eq("7000F3")
        )
        assertEquals(listOf("Reservoir empty", "Bolus stopped"), alarms.map { it.tips })
        // The progress dialog said "delivered successfully"; it now shows what was given.
        verify(bolusProgressData).updateProgress(eq(99), eq(TextRef.Literal("Bolus stopped")), argThat<PumpInsulin> { abs(cU - 11.95) < 0.0001 })
    }

    @Test
    fun `an empty reservoir from before the bolus does not change it`() = runTest {
        prepareHistory()
        val bolus = DetailedBolusInfo().apply { insulin = 2.0; timestamp = bolusStart }
        val result = pumpEnactResultProvider().success(true).enacted(true).bolusDelivered(2.0)
        equilManager.decodeHistory(historyRecord(1714, bolusStart - 60 * minute, 4, 1, 2))

        equilManager.correctBolusIfDeliveryStopped(result, bolus, bolusStart, bolusStart + 80_000, 2.0 / 80)

        assertEquals(2.0, result.bolusDelivered, 0.0001)
        verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun `a bolus the pump stopped as abnormal records what was given (real records 1741-1743)`() = runTest {
        prepareHistory()
        whenever(rh.gs(R.string.equil_history_item18)).thenReturn("Abnormal infusion stopped")
        whenever(rh.gs(eq(R.string.equil_bolus_stopped), anyOrNull(), anyOrNull())).thenReturn("Bolus stopped")
        val alarms = collectAlarms()
        // 1 U sent with 2 s (30 U/min): the pump stopped it in the same second and rewound.
        val bolus = DetailedBolusInfo().apply { insulin = 1.0; timestamp = bolusStart }
        val result = pumpEnactResultProvider().success(true).enacted(true).bolusDelivered(1.0)
        listOf(
            historyRecord(1741, bolusStart, 4, 11, 0),     // TBR end
            historyRecord(1742, bolusStart, 5, 1, 2),      // abnormal infusion stopped
            historyRecord(1743, bolusStart, 4, 8, 0)       // rewind
        ).forEach { equilManager.decodeHistory(it) }

        equilManager.correctBolusIfDeliveryStopped(result, bolus, bolusStart, bolusStart + 40_000, 1.0 / 2)

        // Nothing was given; it used to be recorded as 1 U. The pump also stopped basal.
        assertEquals(0.0, result.bolusDelivered, 0.0001)
        assertEquals(listOf("Abnormal infusion stopped", "Bolus stopped"), alarms.map { it.tips })
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(bolusStart), any(), eq(4800 * minute), any(), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), eq(bolusStart), any(), any()
        )
    }

    @Test
    fun `an unknown battery shows as null until the next reading (#4477)`() {
        equilManager.clearPodState()
        equilManager.setBattery(46)

        equilManager.setBattery(null)
        assertEquals(null, equilManager.equilState?.battery)
        assertEquals(null, equilManager.batteryFlow.value)

        // The next advertisement brings the level of the new battery.
        equilManager.setBattery(95)
        assertEquals(95, equilManager.equilState?.battery)
        assertEquals(95, equilManager.batteryFlow.value)
    }

    @Test
    fun `the first mode after a fresh state records nothing`() = runTest {
        prepareRunMode(RunMode.RUN)
        equilManager.equilState?.runMode = null

        equilManager.setRunMode(RunMode.SUSPEND)

        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `getEquilError should return correct error for valid event`() {
        assertEquals("Error 1", equilManager.getEquilError(4, 2, 2))
        assertEquals("Error 2", equilManager.getEquilError(4, 3, 0))
        assertEquals("Error 3", equilManager.getEquilError(4, 3, 2))
    }

    // The pump closes the link about 10 s after a command, so after every bolus the history request
    // finds no open link. It used to wait its full 22 s timeout for an answer that could not come.
    @Test
    fun `loadEquilHistory returns at once when there is no open link`() {
        whenever(equilBLE.readHistory(any())).thenReturn(false)

        val start = System.currentTimeMillis()
        val result = equilManager.loadEquilHistory(1574)

        assertEquals(-1, result)
        assertTrue(System.currentTimeMillis() - start < 5_000, "must not wait for the 22 s timeout")
        verify(equilBLE).readHistory(any())
    }

    @Test
    fun `getEquilError should return empty string for unknown event`() {
        assertEquals("", equilManager.getEquilError(99, 99, 99))
    }

    @Test
    fun `getEquilError should handle shutdown events`() {
        assertEquals("Shutdown BE", equilManager.getEquilError(4, 6, 1))
        assertEquals("Shutdown", equilManager.getEquilError(4, 6, 2))
    }

    @Test
    fun `a pump rewind is not an alarm (#5077)`() {
        // (4, 8, 0) is "Pump rewind", logged twice at every reservoir change.
        assertEquals("", equilManager.getEquilError(4, 8, 0))
    }

    @Test
    fun `getEquilError should handle port 5 events`() {
        assertEquals("Error 6", equilManager.getEquilError(5, 1, 2))
    }

    @Test
    fun `getEquilError should distinguish port type level combinations`() {
        assertEquals("Error 1", equilManager.getEquilError(4, 2, 2))
        assertEquals("", equilManager.getEquilError(4, 2, 1))
        assertEquals("", equilManager.getEquilError(4, 1, 2))
        assertEquals("", equilManager.getEquilError(3, 2, 2))
    }

    @Test
    fun `equilState should be null initially`() {
        assertEquals(null, equilManager.equilState)
    }

}
