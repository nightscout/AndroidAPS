package app.aaps.pump.equil.manager

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.EquilBLE
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.events.EventEquilResumed
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
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
            notificationManager,
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
        assertEquals("Shutdown", equilManager.getEquilError(4, 8, 0))
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
