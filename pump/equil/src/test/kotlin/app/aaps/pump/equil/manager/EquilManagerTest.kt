package app.aaps.pump.equil.manager

import app.aaps.core.data.model.BS
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
import app.aaps.pump.equil.database.EquilTempBasalRecord
import app.aaps.pump.equil.events.EventEquilAlarm
import app.aaps.pump.equil.events.EventEquilResumed
import app.aaps.pump.equil.data.BolusProfile
import app.aaps.pump.equil.manager.command.BaseCmd
import app.aaps.pump.equil.manager.command.CmdHistoryGet
import app.aaps.pump.equil.manager.command.CmdLargeBasalSet
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
import app.aaps.core.utils.notifyAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.Mockito.mockingDetails
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

    // --- a status read ends the temp basal the pump does not run (a lost cancel answer and a lost check) ---

    @Test
    fun `a status read ends the temp basal the pump does not run`() = runTest {
        prepareRunMode(RunMode.RUN, runningTbrType = PumpSync.TemporaryBasalType.NORMAL)
        equilManager.setTempBasal(EquilTempBasalRecord(30 * 60_000, 0.6, 1000L))
        whenever(pumpSync.syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())).thenReturn(true)

        equilManager.endTempBasalWhenThePumpRunsNone(runsOnPump = false, since = 1000L)

        verify(pumpSync).syncStopTemporaryBasalWithPumpId(any(), any(), eq(PumpType.EQUIL), eq("7000F3"), eq(false))
        assertEquals(null, equilManager.getTempBasal())
    }

    @Test
    fun `a status read keeps the temp basal the pump runs`() = runTest {
        prepareRunMode(RunMode.RUN, runningTbrType = PumpSync.TemporaryBasalType.NORMAL)

        equilManager.endTempBasalWhenThePumpRunsNone(runsOnPump = true, since = 1000L)

        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
    }

    @Test
    fun `a status read decides nothing when the pump does not answer`() = runTest {
        prepareRunMode(RunMode.RUN, runningTbrType = PumpSync.TemporaryBasalType.NORMAL)

        equilManager.endTempBasalWhenThePumpRunsNone(runsOnPump = null, since = 1000L)

        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
    }

    @Test
    fun `a status read does not ask the pump when AAPS has no temp basal`() = runTest {
        prepareRunMode(RunMode.RUN)

        equilManager.endTempBasalThePumpDoesNotRun()

        verify(equilBLE, never()).writeCmd(any())
        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
    }

    @Test
    fun `a status read leaves a suspend alone`() = runTest {
        prepareRunMode(RunMode.SUSPEND, runningTbrType = PumpSync.TemporaryBasalType.PUMP_SUSPEND)

        equilManager.endTempBasalThePumpDoesNotRun()

        verify(equilBLE, never()).writeCmd(any())
        verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())
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
    private fun historyRecord(index: Int, time: Long, port: Int, type: Int, level: Int, rate: Int = 0, largeRate: Int = 0): ByteArray {
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
        d[16] = (largeRate and 0xff).toByte()
        d[17] = (largeRate shr 8).toByte()
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

    /** Found by the pump scenario tests: a lost answer was taken for a bolus the pump never got, and nothing was recorded. */
    @Test
    fun `a bolus command that went out without an answer may run, one that never went out does not`() {
        val command = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 40)
        assertEquals(EquilManager.BolusStart.NOT_SENT, equilManager.bolusStart(command))
        command.commandSent = true
        assertEquals(EquilManager.BolusStart.NO_ANSWER, equilManager.bolusStart(command))
        command.cmdSuccess = true
        assertEquals(EquilManager.BolusStart.STARTED, equilManager.bolusStart(command))
    }

    /**
     * Found by the pump scenario tests: a stopped bolus counted what the 2 s progress timer had reached, 0.40 U where the
     * pump gave 0.27 U. Now from the times, rounded up to the 0.05 U step, never more than asked for.
     */
    @Test
    fun `a stopped bolus counts what was given until the stop, rounded up`() {
        // 1.3 s at 12 U/min (0.2 U/s) is 0.26 U.
        assertEquals(0.30, equilManager.unitsGivenUntil(start = 0, end = 1_300, unitsPerSecond = 0.2, requested = 2.0), 0.0001)
        // Exactly on a step stays on it.
        assertEquals(0.20, equilManager.unitsGivenUntil(start = 0, end = 1_000, unitsPerSecond = 0.2, requested = 2.0), 0.0001)
        // A stop after the end is the whole bolus.
        assertEquals(2.0, equilManager.unitsGivenUntil(start = 0, end = 20_000, unitsPerSecond = 0.2, requested = 2.0), 0.0001)
    }

    /** Found by the pump scenario tests: the answer came before the wait began, and the driver slept the whole timeout. */
    @Test
    fun `an answer that came before the wait ends it at once`() {
        val command = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 40)
        command.cmdSuccess = true
        val start = System.currentTimeMillis()
        equilManager.awaitAnswer(command, timeoutMs = 5_000)
        assertTrue(System.currentTimeMillis() - start < 1_000)
    }

    @Test
    fun `a failed link ends the wait at once`() {
        val command = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 40)
        command.failed = true
        val start = System.currentTimeMillis()
        equilManager.awaitAnswer(command, timeoutMs = 5_000)
        assertTrue(System.currentTimeMillis() - start < 1_000)
    }

    @Test
    fun `a wake-up without an answer does not end the wait`() {
        val command = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 40)
        thread {
            Thread.sleep(50)
            synchronized(command) { command.notifyAll() }
        }
        val start = System.currentTimeMillis()
        equilManager.awaitAnswer(command, timeoutMs = 500)
        assertTrue(System.currentTimeMillis() - start >= 450)
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
        // Not given in full, so the bolus alarm rings.
        assertFalse(result.success)
        assertEquals("Bolus stopped", result.comment)
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
        assertTrue(result.success)
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
        assertFalse(result.success)
        assertEquals(listOf("Abnormal infusion stopped", "Bolus stopped"), alarms.map { it.tips })
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(bolusStart), any(), eq(4800 * minute), any(), eq(PumpSync.TemporaryBasalType.PUMP_SUSPEND), eq(bolusStart), any(), any()
        )
    }

    // --- A bolus whose start or stop got no answer, decided from the pump log ---

    private val sent = now - 10 * minute

    /** 2 U at 1.5 U/min, sent at [sentAt]. Its log entries come after index 1500. */
    private fun uncertainBolus(sentAt: Long = sent, decideAfter: Long = sentAt + 83_000) = UncertainBolus(
        requested = 2.0, unitsPerSecond = 1.0 / 40, indexBefore = 1500, sentAt = sentAt, decideAfter = decideAfter,
        bolusType = BS.Type.NORMAL, pumpId = sentAt - 1000, recordTime = sentAt + 1000
    )

    /** A connection: the advertisement with the newest log index. */
    private fun advertise(newestIndex: Int) {
        val data = ByteArray(28)
        data[23] = (newestIndex and 0xff).toByte()
        data[24] = (newestIndex shr 8).toByte()
        equilManager.decodeData(data)
    }

    @Test
    fun `a bolus that is not in the pump log is recorded as 0 U`() = runTest {
        prepareHistory()
        whenever(rh.gs(eq(R.string.equil_bolus_not_given), anyOrNull())).thenReturn("Not given")
        val alarms = collectAlarms()
        equilManager.uncertainBolus = uncertainBolus()
        advertise(1502)
        // A temp basal after the command, and no bolus.
        equilManager.decodeHistory(historyRecord(1501, sent + 30_000, 4, 10, 0, rate = 160))
        equilManager.decodeHistory(historyRecord(1502, sent + 30_000, 4, 0, 0, rate = 96))

        equilManager.decideUncertainBolus(complete = true)

        verify(pumpSync).syncBolusWithPumpId(
            eq(sent + 1000), argThat<PumpInsulin> { cU == 0.0 }, anyOrNull(), eq(sent - 1000), eq(PumpType.EQUIL), eq("7000F3")
        )
        assertEquals(listOf(EventEquilAlarm("Not given", sound = false)), alarms)
        assertEquals(null, equilManager.uncertainBolus)
    }

    @Test
    fun `a stop without an answer counts what the pump log shows`() = runTest {
        prepareHistory()
        whenever(rh.gs(eq(R.string.equil_bolus_stopped), anyOrNull(), anyOrNull())).thenReturn("Bolus stopped")
        val alarms = collectAlarms()
        equilManager.uncertainBolus = uncertainBolus()
        advertise(1502)
        equilManager.decodeHistory(historyRecord(1501, sent, 4, 0, 0, rate = 160, largeRate = 14400))
        equilManager.decodeHistory(historyRecord(1502, sent + 20_000, 4, 0, 0, rate = 160)) // stopped after 20 s

        equilManager.decideUncertainBolus(complete = true)

        // 21 s x 1.5 U/min = 0.525 U, rounded up to the 0.05 U step: never less than was given.
        verify(pumpSync).syncBolusWithPumpId(
            eq(sent + 1000), argThat<PumpInsulin> { abs(cU - 0.55) < 0.0001 }, anyOrNull(), eq(sent - 1000), eq(PumpType.EQUIL), eq("7000F3")
        )
        assertEquals(listOf(EventEquilAlarm("Bolus stopped", sound = false)), alarms)
    }

    @Test
    fun `a whole bolus in the pump log keeps its record`() = runTest {
        prepareHistory()
        equilManager.uncertainBolus = uncertainBolus()
        advertise(1502)
        equilManager.decodeHistory(historyRecord(1501, sent, 4, 0, 0, rate = 160, largeRate = 14400))
        equilManager.decodeHistory(historyRecord(1502, sent + 80_000, 4, 0, 0, rate = 160))

        equilManager.decideUncertainBolus(complete = true)

        verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
        assertEquals(null, equilManager.uncertainBolus)
    }

    @Test
    fun `the decision waits for a whole read on a connection after the bolus`() = runTest {
        prepareHistory()
        // First, while no advertisement came yet: one with log index 0 has no log in it.
        val noLog = uncertainBolus(sentAt = System.currentTimeMillis() - 60_000, decideAfter = System.currentTimeMillis() - 50_000)
        equilManager.uncertainBolus = noLog
        advertise(0)
        equilManager.decideUncertainBolus(complete = true)
        assertEquals(noLog, equilManager.uncertainBolus)

        // Read before the bolus would have ended: the log may not have all of it yet.
        val running = uncertainBolus(sentAt = System.currentTimeMillis())
        equilManager.uncertainBolus = running
        advertise(1500)
        equilManager.decideUncertainBolus(complete = true)
        assertEquals(running, equilManager.uncertainBolus)

        // A short bolus is over, but an advertisement 10 s after the command may not have its start yet (seen within 20 s).
        val short = uncertainBolus(sentAt = System.currentTimeMillis() - 10_000, decideAfter = System.currentTimeMillis() - 5_000)
        equilManager.uncertainBolus = short
        advertise(1500)
        equilManager.decideUncertainBolus(complete = true)
        assertEquals(short, equilManager.uncertainBolus)

        // A read that did not reach the advertised index.
        equilManager.uncertainBolus = uncertainBolus()
        advertise(1502)
        equilManager.decideUncertainBolus(complete = false)

        // A bolus that started and has no end in the log yet.
        equilManager.decodeHistory(historyRecord(1501, sent, 4, 0, 0, rate = 160, largeRate = 14400))
        equilManager.decideUncertainBolus(complete = true)

        verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
        assertTrue(equilManager.uncertainBolus != null)
    }

    @Test
    fun `a suspend ends the temp basal the Equil screen shows`() = runTest {
        prepareRunMode(RunMode.RUN)
        equilManager.setTempBasal(EquilTempBasalRecord(30 * minute.toInt(), 0.6, now))

        equilManager.setRunMode(RunMode.SUSPEND)

        assertEquals(null, equilManager.getTempBasal())
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

    /**
     * The app restart scenario: the pump gives the whole bolus also when the app is killed meanwhile. Recorded only at the
     * end, a killed app left no record at all.
     */
    @Test
    fun `a bolus is recorded with its whole amount before it runs`() = runTest {
        prepareRunMode(RunMode.RUN)
        whenever(rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)).thenReturn("OK")
        val sentAt = now - 60_000
        // The pump answers every command at once.
        doAnswer { (it.arguments[0] as BaseCmd).apply { commandSent = true; commandSentAt = sentAt; cmdSuccess = true }; Unit }
            .whenever(equilBLE).writeCmd(any())
        var recordedBeforeProgress = false
        doAnswer { recordedBeforeProgress = mockingDetails(pumpSync).invocations.any { it.method.name == "syncBolusWithPumpId" }; Unit }
            .whenever(bolusProgressData).updateProgress(0)
        val bolus = DetailedBolusInfo().apply { insulin = 2.0; timestamp = sentAt - 1000 }

        equilManager.bolus(bolus, BolusProfile())

        assertTrue(recordedBeforeProgress)
        // The start and the end, both at the bolus start, the same record.
        verify(pumpSync, times(2)).syncBolusWithPumpId(
            eq(sentAt), argThat<PumpInsulin> { abs(cU - 2.0) < 0.0001 }, anyOrNull(), eq(sentAt - 1000), eq(PumpType.EQUIL), eq("7000F3")
        )
    }

    /** The link answers every history request at once. */
    private fun answerHistoryReads() {
        whenever(equilBLE.readHistory(any())).thenAnswer { (it.arguments[0] as CmdHistoryGet).cmdSuccess = true; true }
    }

    @Test
    fun `a new log is read from its first entry`() {
        prepareRunMode(RunMode.RUN)
        answerHistoryReads()
        equilManager.setStartHistoryIndex(0)
        equilManager.setHistoryIndex(3)

        equilManager.loadEquilHistory()

        verify(equilBLE, times(3)).readHistory(any())
        assertEquals(3, equilManager.getStartHistoryIndex())
    }

    @Test
    fun `the log is read on after it started again at 1`() {
        prepareRunMode(RunMode.RUN)
        answerHistoryReads()
        equilManager.setStartHistoryIndex(1999)
        equilManager.setHistoryIndex(2)

        equilManager.loadEquilHistory()

        // 2000, then 1 and 2.
        verify(equilBLE, times(3)).readHistory(any())
        assertEquals(2, equilManager.getStartHistoryIndex())
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
