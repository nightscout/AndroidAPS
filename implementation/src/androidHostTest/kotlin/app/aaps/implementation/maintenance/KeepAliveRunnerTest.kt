package app.aaps.implementation.maintenance

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.keys.LongNonKey
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.minutes

/**
 * The keep-alive checks, as every platform runs them. `KeepAliveWorkerTest` covers what only Android has: the
 * WorkManager schedule and the WorkManager database checks.
 */
class KeepAliveRunnerTest : TestBaseWithProfile() {

    private lateinit var runner: KeepAliveRunner

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var maintenance: Maintenance
    @Mock private lateinit var dstHelper: DstHelper
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var ads: AutosensDataStore
    @Mock private lateinit var localAlertUtils: LocalAlertUtils
    @Mock private lateinit var mockedRxBus: RxBus

    @BeforeEach
    fun setUp() {
        whenever(iobCobCalculator.ads).thenReturn(ads)
        // Short-circuit Config.awaitInitialized() so runOnce() proceeds past the init gate.
        // Without this the suspend gate hits initProgressFlow (null on a fresh Mock) and NPEs.
        whenever(config.appInitialized).thenReturn(true)
        // Default: cleanup ran "now" so the database cleanup is a no-op unless a test opts in.
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now)
    }

    private fun createRunner(): KeepAliveRunner =
        KeepAliveRunner(
            aapsLogger = aapsLogger,
            fabricPrivacy = fabricPrivacy,
            localAlertUtils = localAlertUtils,
            config = config,
            iobCobCalculator = iobCobCalculator,
            loop = loop,
            dateUtil = dateUtil,
            activePlugin = activePlugin,
            profileFunction = profileFunction,
            rxBus = mockedRxBus,
            commandQueue = commandQueue,
            // The real shared component, not a mock: the assertions below check the work it does.
            periodicMaintenance = PeriodicMaintenance(aapsLogger, localAlertUtils, persistenceLayer, maintenance, preferences, dateUtil),
            // Real English, so the reason given to the queue is checked as the user sees it.
            rh = baseText,
            dstHelper = dstHelper,
            ch = ch
        )

    // ---- runOnce: gating and order ----------------------------------------------------------------

    @Test
    fun `runOnce runs the maintenance pipeline`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)

        val outcome = runner.runOnce()

        assertThat(outcome).isEqualTo(WorkOutcome.Success)
        verify(dstHelper).dstCheck()
        verify(localAlertUtils).shortenSnoozeInterval()
        verify(localAlertUtils).checkStaleBGAlert()
        verify(maintenance).deleteLogs(30)
    }

    @Test
    fun `runOnce skips a pass that comes sooner than the minimum interval`() = runTest {
        runner = createRunner()
        runner.lastRun = now - T.mins(3).msecs()

        val outcome = runner.runOnce(minInterval = 4.minutes)

        assertThat(outcome).isInstanceOf(WorkOutcome.Skipped::class.java)
        verify(dstHelper, never()).dstCheck()
        verify(maintenance, never()).deleteLogs(any())
    }

    @Test
    fun `runOnce runs a pass that comes after the minimum interval`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        runner.lastRun = now - T.mins(5).msecs()

        val outcome = runner.runOnce(minInterval = 4.minutes)

        assertThat(outcome).isEqualTo(WorkOutcome.Success)
        verify(dstHelper).dstCheck()
    }

    @Test
    fun `runOnce without a minimum interval runs also right after the last pass`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        runner.lastRun = now

        val outcome = runner.runOnce()

        assertThat(outcome).isEqualTo(WorkOutcome.Success)
        verify(dstHelper).dstCheck()
    }

    @Test
    fun `runOnce asks to be run again when the app is not yet initialized`() = runTest {
        runner = createRunner()
        whenever(config.appInitialized).thenReturn(false)
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))

        val outcome = runner.runOnce()

        assertThat(outcome).isInstanceOf(WorkOutcome.Retry::class.java)
        verify(dstHelper, never()).dstCheck()
        assertThat(runner.lastRun).isEqualTo(0L)
    }

    @Test
    fun `a second pass while one is running is skipped`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        val gate = CompletableDeferred<Unit>()
        whenever(loop.verifyZeroDelivery()).doSuspendableAnswer { gate.await() }
        val first = launch { runner.runOnce() }
        runCurrent()

        val second = runner.runOnce()

        assertThat(second).isInstanceOf(WorkOutcome.Skipped::class.java)
        verify(dstHelper, times(1)).dstCheck()
        gate.complete(Unit)
        first.join()
        // The lock is free again once the first pass ends.
        assertThat(runner.runOnce()).isEqualTo(WorkOutcome.Success)
    }

    @Test
    fun `runOnce records when the pass started`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)

        runner.runOnce()

        assertThat(runner.lastRun).isEqualTo(now)
    }

    @Test
    fun `runOnce retries a missing zero temp basal`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)

        runner.runOnce()

        verify(loop).verifyZeroDelivery()
    }

    @Test
    fun `a pass more than 10 minutes after the last one reports the gap`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        runner.lastRun = now - T.mins(11).msecs()

        runner.runOnce()

        verify(fabricPrivacy).logCustom("KeepAliveFail")
        verify(dstHelper).dstCheck()
    }

    @Test
    fun `a pass less than 10 minutes after the last one reports no gap`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        runner.lastRun = now - T.mins(6).msecs()

        runner.runOnce()

        verify(fabricPrivacy, never()).logCustom("KeepAliveFail")
        verify(dstHelper).dstCheck()
    }

    // ---- checkPump --------------------------------------------------------------------------------

    @Test
    fun `checkPump requests status when connection is outdated`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)
        testPumpPlugin.lastData = now - T.mins(20).msecs()

        runner.checkPump()

        verify(commandQueue).readStatus("KeepAlive. Status outdated.")
    }

    @Test
    fun `checkPump requests status when basal is outdated`() = runTest {
        // Connection is fresh (status not outdated) but base basal differs from requested
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)
        testPumpPlugin.lastData = now // fresh -> status not outdated; requested basal 1.0 vs pump 0.0 -> basal outdated

        runner.checkPump()

        verify(commandQueue).readStatus("KeepAlive. Basal outdated.")
    }

    @Test
    fun `checkPump sends profile switch event if profile is mismatched`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        testPumpPlugin.isProfileSet = false

        runner.checkPump()

        verify(mockedRxBus).send(any<EventProfileChangeRequested>())
    }

    /** #2454: the pump rounded the basal, so the running profile holds other rates than the requested one. */
    private suspend fun requestedPsRunningFrom(psId: Long) {
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch.copy(id = 140))
        // Built before whenever(): the constructor reads activePlugin, a mock.
        val running = ProfileSealed.EPS(effectiveProfileSwitch.copy(originalPsId = psId, basalBlocks = listOf(Block(T.hours(24).msecs(), 0.95))), activePlugin)
        whenever(profileFunction.getProfile()).thenReturn(running)
        testPumpPlugin.isProfileSet = true
        testPumpPlugin.lastData = now
    }

    @Test
    fun `checkPump does not set a profile again because the pump rounded its basal`() = runTest {
        runner = createRunner()
        requestedPsRunningFrom(140)

        runner.checkPump()

        verify(mockedRxBus, never()).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump sets the profile when the running one was made from another switch`() = runTest {
        runner = createRunner()
        requestedPsRunningFrom(139)

        runner.checkPump()

        verify(mockedRxBus).send(any<EventProfileChangeRequested>())
    }

    /**
     * #2454: the Combo gives 1.10 for 1.116, and its step is 0.01. Compared with the requested profile, the basal
     * looked outdated at every run and the pump was read every 5 minutes. The running profile holds the 1.10.
     */
    @Test
    fun `checkPump does not read the status when the pump gives the rounded rate of the running profile`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch.copy(id = 140, basalBlocks = listOf(Block(T.hours(24).msecs(), 1.116))))
        // Built before whenever(): the constructor reads activePlugin, a mock.
        val running = ProfileSealed.EPS(effectiveProfileSwitch.copy(originalPsId = 140, basalBlocks = listOf(Block(T.hours(24).msecs(), 1.10))), activePlugin)
        whenever(profileFunction.getProfile()).thenReturn(running)
        whenever(ch.fromPump(any<PumpRate>())).thenReturn(1.10)
        testPumpPlugin.isProfileSet = true
        testPumpPlugin.lastData = now

        runner.checkPump()

        verify(commandQueue, never()).readStatus(any())
        verify(mockedRxBus, never()).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump still reads the status when the pump gives another rate than the running profile`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch.copy(id = 140))
        val running = ProfileSealed.EPS(effectiveProfileSwitch.copy(originalPsId = 140, basalBlocks = listOf(Block(T.hours(24).msecs(), 1.10))), activePlugin)
        whenever(profileFunction.getProfile()).thenReturn(running)
        whenever(ch.fromPump(any<PumpRate>())).thenReturn(0.9)
        testPumpPlugin.isProfileSet = true
        testPumpPlugin.lastData = now

        runner.checkPump()

        verify(commandQueue).readStatus("KeepAlive. Basal outdated.")
    }

    @Test
    fun `checkPump does nothing if mode is DISCONNECTED_PUMP`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.DISCONNECTED_PUMP)
        testPumpPlugin.lastData = now - T.mins(20).msecs()

        runner.runOnce()

        verify(commandQueue, never()).readStatus(any())
        verify(mockedRxBus, never()).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump does nothing when there is no requested profile`() = runTest {
        runner = createRunner()
        whenever(profileFunction.getRequestedProfile()).thenReturn(null)

        runner.checkPump()

        verify(commandQueue, never()).readStatus(any())
        verify(mockedRxBus, never()).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump does not request status while the pump is busy`() = runTest {
        // Outdated connection but a busy pump must not be queried
        val busyPump = mock<PumpWithConcentration>()
        whenever(busyPump.isBusy()).thenReturn(true)
        whenever(busyPump.lastDataTime).thenReturn(MutableStateFlow(now - T.mins(60).msecs()))
        whenever(busyPump.baseBasalRate).thenReturn(PumpRate(0.0))
        whenever(busyPump.pumpDescription).thenReturn(PumpDescription())
        whenever(activePlugin.activePump).thenReturn(busyPump)
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)

        runner.checkPump()

        verify(commandQueue, never()).readStatus(any())
    }

    /** The pump was last read 20 minutes ago, so each check asks for its status. No profile is sent. */
    private suspend fun pumpStatusOutdated(mode: RM.Mode = RM.Mode.OPEN_LOOP) {
        whenever(loop.runningMode()).thenReturn(mode)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)
        testPumpPlugin.lastData = now - T.mins(20).msecs()
    }

    @Test
    fun `the pump unreachable alarm is checked only after a status read was asked for`() = runTest {
        runner = createRunner()
        pumpStatusOutdated()

        runner.checkPump()
        verify(localAlertUtils, never()).checkPumpUnreachableAlarm(any(), any(), any())

        runner.checkPump()
        verify(localAlertUtils).checkPumpUnreachableAlarm(now - T.mins(20).msecs(), true, false)
    }

    @Test
    fun `a status read asked for 5 minutes 20 seconds ago still lets the pump unreachable alarm be checked`() = runTest {
        runner = createRunner()
        pumpStatusOutdated()
        runner.lastReadStatus = now - T.secs(5 * 60 + 20).msecs()

        runner.checkPump()

        verify(localAlertUtils).checkPumpUnreachableAlarm(now - T.mins(20).msecs(), true, false)
    }

    @Test
    fun `a status read asked for 6 minutes ago does not let the pump unreachable alarm be checked`() = runTest {
        runner = createRunner()
        pumpStatusOutdated()
        runner.lastReadStatus = now - T.mins(6).msecs()

        runner.checkPump()

        verify(localAlertUtils, never()).checkPumpUnreachableAlarm(any(), any(), any())
    }

    @Test
    fun `the pump unreachable alarm is told that the pump is disconnected`() = runTest {
        runner = createRunner()
        pumpStatusOutdated(RM.Mode.DISCONNECTED_PUMP)
        runner.lastReadStatus = now - T.mins(5).msecs()

        runner.checkPump()

        verify(localAlertUtils).checkPumpUnreachableAlarm(now - T.mins(20).msecs(), true, true)
        verify(commandQueue, never()).readStatus(any())
    }

    /** A temporary profile of one hour, made from profile switch 140, which ends at [end]. */
    private suspend fun runningTemporaryProfileEnding(end: Long) {
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch.copy(id = 140))
        // Built before whenever(): the constructor reads activePlugin, a mock.
        val running = ProfileSealed.EPS(effectiveProfileSwitch.copy(originalPsId = 140, originalDuration = T.hours(1).msecs(), originalEnd = end), activePlugin)
        whenever(profileFunction.getProfile()).thenReturn(running)
        testPumpPlugin.isProfileSet = true
        testPumpPlugin.lastData = now
    }

    @Test
    fun `checkPump sets the profile again when the running temporary profile has ended`() = runTest {
        runner = createRunner()
        runningTemporaryProfileEnding(now - T.mins(1).msecs())

        runner.checkPump()

        verify(mockedRxBus).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump leaves a running temporary profile that has not ended`() = runTest {
        runner = createRunner()
        runningTemporaryProfileEnding(now + T.mins(1).msecs())

        runner.checkPump()

        verify(mockedRxBus, never()).send(any<EventProfileChangeRequested>())
    }

    // ---- checkAPS ---------------------------------------------------------------------------------

    @Test
    fun `checkAPS schedules device status upload if BG is missing`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(ads.actualBg()).thenReturn(null)

        runner.runOnce()

        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `checkAPS does not upload device status in client mode`() = runTest {
        runner = createRunner()
        whenever(config.AAPSCLIENT).thenReturn(true)

        runner.checkAPS()

        verify(loop, never()).scheduleBuildAndStoreDeviceStatus(any())
    }

    @Test
    fun `checkAPS uploads device status in pump control mode`() = runTest {
        runner = createRunner()
        whenever(config.PUMPCONTROL).thenReturn(true)

        runner.checkAPS()

        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `checkAPS does not upload when loop is running and BG is present and APS is recent`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(ads.actualBg()).thenReturn(mock<InMemoryGlucoseValue>())
        whenever(activePlugin.activeAPS).thenReturn(null)

        runner.checkAPS()

        verify(loop, never()).scheduleBuildAndStoreDeviceStatus(any())
    }

    @Test
    fun `checkAPS uploads device status when last APS run is stale`() = runTest {
        runner = createRunner()
        val aps = mock<APS>()
        whenever(aps.lastAPSRun).thenReturn(now - T.mins(10).msecs())
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(ads.actualBg()).thenReturn(mock<InMemoryGlucoseValue>())
        whenever(activePlugin.activeAPS).thenReturn(aps)

        runner.checkAPS()

        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `checkAPS uploads the status at most every 5 minutes`() = runTest {
        runner = createRunner()
        whenever(config.PUMPCONTROL).thenReturn(true)

        runner.checkAPS()
        runner.checkAPS()

        verify(loop, times(1)).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `checkAPS uploads the status again when the last upload is more than 5 minutes old`() = runTest {
        runner = createRunner()
        whenever(config.PUMPCONTROL).thenReturn(true)
        runner.lastIobUpload = now - T.mins(6).msecs()

        runner.checkAPS()

        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `checkAPS does not upload the status when the last upload is less than 5 minutes old`() = runTest {
        runner = createRunner()
        whenever(config.PUMPCONTROL).thenReturn(true)
        runner.lastIobUpload = now - T.mins(4).msecs()

        runner.checkAPS()

        verify(loop, never()).scheduleBuildAndStoreDeviceStatus(any())
    }

    // ---- database cleanup -------------------------------------------------------------------------

    @Test
    fun `databaseCleanup does NOT run if it was run less than a day ago`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.hours(12).msecs())

        runner.runOnce()

        verify(persistenceLayer, never()).cleanupDatabase(any(), any())
    }

    @Test
    fun `databaseCleanup runs and records the timestamp when last run was over a day ago`() = runTest {
        runner = createRunner()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.days(2).msecs())
        whenever(persistenceLayer.cleanupDatabase(any(), any())).thenReturn("done")

        runner.runOnce()

        // Keeps ~6 months and stamps the run time
        verify(persistenceLayer).cleanupDatabase(now - T.days(6 * 31).msecs(), false)
        verify(preferences).put(LongNonKey.LastCleanupRun, now)
    }
}
