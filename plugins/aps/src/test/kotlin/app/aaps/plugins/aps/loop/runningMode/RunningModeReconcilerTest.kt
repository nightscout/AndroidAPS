package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RunningModeReconcilerTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var commandQueue: CommandQueue

    private lateinit var reconciler: RunningModeReconciler
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach
    fun prepare() {
        whenever(config.APS).thenReturn(true)
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        reconciler = RunningModeReconciler(
            persistenceLayer = persistenceLayer,
            processedTbrEbData = processedTbrEbData,
            activePlugin = activePlugin,
            commandQueue = commandQueue,
            profileFunction = profileFunction,
            config = config,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger,
            appScope = testScope
        )
    }

    // --- config.APS gate ---

    @Test
    fun `does not issue pump commands when config APS is false`() = runTest {
        whenever(config.APS).thenReturn(false)
        val mode = workingMode(RM.Mode.CLOSED_LOOP)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        reconciler.start()
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any(), anyOrNull())
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any(), anyOrNull())
        verify(commandQueue, never()).cancelTempBasal(any(), any(), anyOrNull())
    }

    // --- Startup: working mode, pump clean ---

    @Test
    fun `startup with working mode and no pump TBR issues no commands`() = runTest {
        val mode = workingMode(RM.Mode.CLOSED_LOOP)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        reconciler.start()
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any(), anyOrNull())
        verify(commandQueue, never()).cancelTempBasal(any(), any(), anyOrNull())
    }

    // --- Startup: zero-delivery mode, pump not zero ---

    @Test
    fun `startup with DISCONNECTED_PUMP issues zero TBR when pump is not already zero`() = runTest {
        testPumpPlugin.pumpDescription = PumpDescription().apply {
            tempBasalStyle = PumpDescription.ABSOLUTE
        }
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        val nowValue = now
        val activeMode = temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = nowValue, durationMs = T.mins(30).msecs())
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(activeMode)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        verify(commandQueue).tempBasalAbsolute(
            eq(0.0), eq(60), eq(true), any(),
            eq(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND), any<Callback>()
        )
    }

    // --- Startup idempotency ---

    @Test
    fun `startup with DISCONNECTED_PUMP skips zero TBR when pump already zero for sufficient window`() = runTest {
        testPumpPlugin.pumpDescription = PumpDescription().apply {
            tempBasalStyle = PumpDescription.ABSOLUTE
        }
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        val nowValue = now
        val activeMode = temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = nowValue, durationMs = T.mins(30).msecs())
        val zeroTbr = TB(
            timestamp = nowValue,
            type = TB.Type.EMULATED_PUMP_SUSPEND,
            isAbsolute = true,
            rate = 0.0,
            duration = T.mins(60).msecs()
        )
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(activeMode)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(zeroTbr)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any(), anyOrNull())
    }

    // --- Startup drift: working mode but pump has stale zero-TBR ---

    @Test
    fun `startup drift cancels stale EMULATED_PUMP_SUSPEND TBR when mode is working`() = runTest {
        val nowValue = now
        val mode = workingMode(RM.Mode.CLOSED_LOOP)
        val staleTbr = TB(
            timestamp = nowValue - T.mins(10).msecs(),
            type = TB.Type.EMULATED_PUMP_SUSPEND,
            isAbsolute = true,
            rate = 0.0,
            duration = T.mins(60).msecs()
        )
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(staleTbr)

        reconciler.start()

        verify(commandQueue).cancelTempBasal(eq(true), any(), any<Callback>())
    }

    // --- Transition: CLOSED_LOOP -> DISCONNECTED_PUMP ---

    @Test
    fun `transition to DISCONNECTED_PUMP issues zero TBR`() = runTest {
        testPumpPlugin.pumpDescription = PumpDescription().apply {
            tempBasalStyle = PumpDescription.ABSOLUTE
        }
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        val nowValue = now
        val workingModeRm = workingMode(RM.Mode.CLOSED_LOOP)
        val disconnect = temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = nowValue, durationMs = T.mins(30).msecs())
        val flow = MutableSharedFlow<List<RM>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(RM::class.java))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingModeRm)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(disconnect)
        flow.emit(listOf(disconnect))

        verify(commandQueue).tempBasalAbsolute(
            eq(0.0), eq(60), eq(true), any(),
            eq(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND), any<Callback>()
        )
    }

    // --- Transition: DISCONNECTED_PUMP -> CLOSED_LOOP ---

    @Test
    fun `transition from DISCONNECTED_PUMP to working cancels TBR`() = runTest {
        val nowValue = now
        val activeDisc = temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = nowValue, durationMs = T.mins(30).msecs())
        val working = workingMode(RM.Mode.CLOSED_LOOP)
        val zeroTbr = TB(
            timestamp = nowValue, type = TB.Type.EMULATED_PUMP_SUSPEND,
            isAbsolute = true, rate = 0.0, duration = T.mins(60).msecs()
        )
        val flow = MutableSharedFlow<List<RM>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(RM::class.java))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(activeDisc)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(zeroTbr)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(working)
        flow.emit(listOf(working))

        verify(commandQueue).cancelTempBasal(eq(true), any(), any<Callback>())
    }

    // --- Transition: CLOSED_LOOP -> SUSPENDED_BY_USER with active TBR ---

    @Test
    fun `transition to SUSPENDED_BY_USER cancels active TBR`() = runTest {
        val nowValue = now
        val workingModeRm = workingMode(RM.Mode.CLOSED_LOOP)
        val suspended = temporaryMode(RM.Mode.SUSPENDED_BY_USER, timestamp = nowValue, durationMs = T.mins(30).msecs())
        val activeTbr = TB(
            timestamp = nowValue, type = TB.Type.NORMAL,
            isAbsolute = true, rate = 1.5, duration = T.mins(30).msecs()
        )
        val flow = MutableSharedFlow<List<RM>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(RM::class.java))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingModeRm)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(activeTbr)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(suspended)
        flow.emit(listOf(suspended))

        verify(commandQueue).cancelTempBasal(eq(true), any(), any<Callback>())
    }

    // --- Extended bolus cancel on entry to zero-delivery ---

    @Test
    fun `transition to DISCONNECTED_PUMP cancels active extended bolus`() = runTest {
        testPumpPlugin.pumpDescription = PumpDescription().apply {
            tempBasalStyle = PumpDescription.ABSOLUTE
        }
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        val nowValue = now
        val workingModeRm = workingMode(RM.Mode.CLOSED_LOOP)
        val disconnect = temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = nowValue, durationMs = T.mins(30).msecs())
        val activeEb = EB(
            timestamp = nowValue - T.mins(5).msecs(),
            amount = 3.0,
            duration = T.mins(30).msecs(),
            isEmulatingTempBasal = false
        )
        val flow = MutableSharedFlow<List<RM>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(RM::class.java))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingModeRm)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(disconnect)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(activeEb)
        flow.emit(listOf(disconnect))

        verify(commandQueue).cancelExtended(any<Callback>())
    }

    // --- Helpers ---

    private fun workingMode(mode: RM.Mode) = RM(
        id = mode.ordinal.toLong() + 1,
        timestamp = now,
        mode = mode,
        duration = 0L
    )

    private fun temporaryMode(mode: RM.Mode, timestamp: Long, durationMs: Long) = RM(
        id = (mode.ordinal.toLong() + 100),
        timestamp = timestamp,
        mode = mode,
        duration = durationMs
    )
}
