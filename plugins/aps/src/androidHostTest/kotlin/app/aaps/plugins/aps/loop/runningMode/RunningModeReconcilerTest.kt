package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.R
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

class RunningModeReconcilerTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var commandQueue: CommandQueue

    private lateinit var reconciler: RunningModeReconciler
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach
    fun prepare() {
        whenever(config.APS).thenReturn(true)
        whenever(persistenceLayer.observeChanges(anyOrNull<KClass<*>>())).thenReturn(emptyFlow())
        runBlocking {
            whenever(commandQueue.cancelTempBasal(anyBoolean(), anyBoolean())).thenReturn(pumpEnactResultProvider().success(true))
            whenever(commandQueue.tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())).thenReturn(pumpEnactResultProvider().success(true))
            whenever(commandQueue.cancelExtended()).thenReturn(pumpEnactResultProvider().success(true))
        }
        reconciler = RunningModeReconciler(
            persistenceLayer = persistenceLayer,
            processedTbrEbData = processedTbrEbData,
            activePlugin = activePlugin,
            commandQueue = commandQueue,
            profileFunction = profileFunction,
            config = config,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger,
            rxBus = rxBus,
            rh = rh,
            notificationManager = notificationManager,
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
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any())
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any())
        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }

    // --- Startup: working mode, pump clean ---

    @Test
    fun `startup with working mode and no pump TBR issues no commands`() = runTest {
        val mode = workingMode(RM.Mode.CLOSED_LOOP)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        reconciler.start()
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any())
        verify(commandQueue, never()).cancelTempBasal(any(), any())
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
            eq(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND)
        )
    }

    /**
     * Nothing is issued while the command queue is held for a settings import.
     *
     * This method cancels an extended bolus and THEN issues a zero temp basal. Held, the executor picks
     * up neither - so starting anyway would cancel the extended bolus, leave the zero TBR waiting in the
     * queue, and run FULL BASAL while the app shows the pump as suspended. Skipping is safe: the
     * reconciler runs again and re-derives the whole state from the mode.
     */
    @Test
    fun `issues nothing while the queue is held for an import`() = runTest {
        testPumpPlugin.pumpDescription = PumpDescription().apply {
            tempBasalStyle = PumpDescription.ABSOLUTE
        }
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        val activeMode = temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = now, durationMs = T.mins(30).msecs())
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(activeMode)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)
        whenever(commandQueue.isHeld()).thenReturn(true)

        reconciler.start()

        verify(commandQueue, never()).tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())
        verify(commandQueue, never()).cancelExtended()
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

        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any())
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

        verify(commandQueue).cancelTempBasal(eq(true), any())
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
        whenever(persistenceLayer.observeChanges(eq(RM::class))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingModeRm)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(disconnect)
        flow.emit(listOf(disconnect))

        verify(commandQueue).tempBasalAbsolute(
            eq(0.0), eq(60), eq(true), any(),
            eq(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND)
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
        whenever(persistenceLayer.observeChanges(eq(RM::class))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(activeDisc)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(zeroTbr)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(working)
        flow.emit(listOf(working))

        verify(commandQueue).cancelTempBasal(eq(true), any())
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
        whenever(persistenceLayer.observeChanges(eq(RM::class))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingModeRm)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(activeTbr)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(suspended)
        flow.emit(listOf(suspended))

        verify(commandQueue).cancelTempBasal(eq(true), any())
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
        whenever(persistenceLayer.observeChanges(eq(RM::class))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingModeRm)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(disconnect)
        whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(activeEb)
        flow.emit(listOf(disconnect))

        verify(commandQueue).cancelExtended()
    }

    // --- Same stopped mode again (extend a suspend) ---

    @Test
    fun `extending a suspend keeps a TBR the user set by hand`() = runTest {
        val first = RM(id = 1, timestamp = now - T.mins(20).msecs(), mode = RM.Mode.SUSPENDED_BY_USER, duration = T.hours(1).msecs())
        val extended = RM(id = 2, timestamp = now, mode = RM.Mode.SUSPENDED_BY_USER, duration = T.hours(2).msecs())
        val manualTbr = TB(timestamp = now - T.mins(10).msecs(), type = TB.Type.NORMAL, isAbsolute = true, rate = 0.5, duration = T.mins(60).msecs())
        val flow = MutableSharedFlow<List<RM>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(RM::class))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(first)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)

        reconciler.start()

        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(manualTbr)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(extended)
        flow.emit(listOf(extended))

        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }

    // --- verifyZeroDelivery ---

    @Test
    fun `verifyZeroDelivery does nothing when the reconciler does not drive the pump`() = runTest {
        whenever(config.APS).thenReturn(false)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = now, durationMs = T.mins(60).msecs()))
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        reconciler.start()

        reconciler.verifyZeroDelivery()

        verify(commandQueue, never()).tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `verifyZeroDelivery does nothing in a working mode`() = runTest {
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingMode(RM.Mode.CLOSED_LOOP))
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        reconciler.start()

        reconciler.verifyZeroDelivery()

        verify(commandQueue, never()).tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())
        verifyAlarmPosted(count =0)
    }

    @Test
    fun `verifyZeroDelivery sends a failed zero TBR again`() = runTest {
        stubZeroDeliveryPump()
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = now, durationMs = T.mins(60).msecs()))
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(commandQueue.tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())).thenReturn(pumpEnactResultProvider().success(false))
        reconciler.start() // first try fails

        reconciler.verifyZeroDelivery()

        verify(commandQueue, times(2)).tempBasalAbsolute(eq(0.0), eq(60), eq(true), any(), eq(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND))
    }

    @Test
    fun `verifyZeroDelivery leaves a zero TBR that covers the mode alone`() = runTest {
        stubZeroDeliveryPump()
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = now, durationMs = T.mins(60).msecs()))
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(zeroTbr(start = now, minutes = 60))
        reconciler.start()

        reconciler.verifyZeroDelivery()

        verify(commandQueue, never()).tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())
        verifyAlarmPosted(count =0)
    }

    @Test
    fun `verifyZeroDelivery renews a zero TBR that ends soon while the mode goes on`() = runTest {
        stubZeroDeliveryPump()
        // The pump took only 2 h of zero TBR (its maximum), the disconnect is 3 h, and 10 min are left of the TBR
        val mode = temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = now - T.mins(110).msecs(), durationMs = T.hours(3).msecs())
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        // Covered at start, so the startup reconcile sends nothing
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(zeroTbr(start = now - T.mins(110).msecs(), minutes = 180))
        reconciler.start()

        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(zeroTbr(start = now - T.mins(110).msecs(), minutes = 120))
        reconciler.verifyZeroDelivery()

        // 70 min are left of the mode, rounded up to the 60 min pump step
        verify(commandQueue).tempBasalAbsolute(eq(0.0), eq(120), eq(true), any(), eq(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND))
    }

    @Test
    fun `alarm rings after the zero TBR stays missing for 10 min and repeats every 15 min`() = runTest {
        stubZeroDeliveryPump()
        val start = now
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = start, durationMs = T.hours(2).msecs()))
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        whenever(commandQueue.tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), anyOrNull(), anyOrNull())).thenReturn(pumpEnactResultProvider().success(false))
        reconciler.start() // missing from now on

        checkAt(start + T.mins(5).msecs())
        verifyAlarmPosted(count =0)

        checkAt(start + T.mins(10).msecs())
        verifyAlarmPosted(count =1)

        checkAt(start + T.mins(15).msecs())
        verifyAlarmPosted(count =1)

        checkAt(start + T.mins(25).msecs())
        verifyAlarmPosted(count =2)

        // The zero TBR finally got through: the alarm goes away
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(zeroTbr(start = start + T.mins(30).msecs(), minutes = 120))
        checkAt(start + T.mins(30).msecs())
        verify(notificationManager).dismiss(NotificationId.ZERO_DELIVERY_NOT_SET)
    }

    @Test
    fun `no alarm when the pump cannot do a temp basal at all`() = runTest {
        stubZeroDeliveryPump()
        testPumpPlugin.pumpDescription.isTempBasalCapable = false
        val start = now
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(temporaryMode(RM.Mode.DISCONNECTED_PUMP, timestamp = start, durationMs = T.hours(2).msecs()))
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(anyLong())).thenReturn(null)
        reconciler.start()

        checkAt(start + T.mins(30).msecs())

        verifyAlarmPosted(count =0)
    }

    // --- Helpers ---

    private fun stubZeroDeliveryPump() {
        testPumpPlugin.pumpDescription = PumpDescription().apply {
            tempBasalStyle = PumpDescription.ABSOLUTE
        }
        // The snackbar text on a failed zero TBR
        whenever(rh.gs(R.string.temp_basal_delivery_error)).thenReturn("Tempbasal delivery error")
        runBlocking {
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
            whenever(persistenceLayer.getExtendedBolusActiveAt(anyLong())).thenReturn(null)
        }
    }

    private suspend fun checkAt(time: Long) {
        whenever(dateUtil.now()).thenReturn(time)
        reconciler.verifyZeroDelivery()
    }

    private fun zeroTbr(start: Long, minutes: Long) = TB(
        timestamp = start,
        type = TB.Type.EMULATED_PUMP_SUSPEND,
        isAbsolute = true,
        rate = 0.0,
        duration = T.mins(minutes).msecs()
    )

    private fun verifyAlarmPosted(count: Int) {
        verify(notificationManager, times(count)).post(
            eq(NotificationId.ZERO_DELIVERY_NOT_SET), any<TextRef>(), any(), any(), any(), any(), eq(AlarmSound.ALARM), any(), anyOrNull()
        )
    }

    @Suppress("SameParameterValue")
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
