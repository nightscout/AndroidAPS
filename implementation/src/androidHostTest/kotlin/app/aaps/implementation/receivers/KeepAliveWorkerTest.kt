package app.aaps.implementation.receivers

import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
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
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class KeepAliveWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: KeepAliveWorker

    @Mock private lateinit var loop: Loop
    @Mock private lateinit var maintenance: Maintenance
    @Mock private lateinit var dstHelper: DstHelper
    @Mock private lateinit var workerParameters: WorkerParameters
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var ads: AutosensDataStore
    @Mock private lateinit var localAlertUtils: LocalAlertUtils
    @Mock private lateinit var workManager: WorkManager
    @Mock private lateinit var listenableFuture: ListenableFuture<List<WorkInfo>>
    @Mock private lateinit var mockedRxBus: RxBus

    @BeforeEach
    fun setUp() {
        // KeepAliveWorker keeps lastRun / lastReadStatus / lastIobUpload as companion (static) state
        // that survives between test methods in the same JVM. Reset it before each test so the cases
        // are deterministic and order-independent.
        resetWorkerStaticState()
        // Configure mocks provided by the base class or declared here.
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(workManager.getWorkInfos(any())).thenReturn(listenableFuture)
        whenever(listenableFuture.get()).thenReturn(emptyList())
        whenever(workerParameters.inputData).thenReturn(workDataOf("schedule" to "KA_5"))
        // Short-circuit Config.awaitInitialized() so doWorkAndLog() proceeds past the init gate.
        // Without this the suspend gate hits initProgressFlow (null on a fresh Mock) and NPEs.
        whenever(config.appInitialized).thenReturn(true)
        // Default: cleanup ran "now" so databaseCleanup() is a no-op unless a test opts in.
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now)
    }

    // KeepAliveWorker uses constructor injection (@HiltWorker / @AssistedInject), so dependencies
    // are passed as constructor arguments.
    private fun createWorker(): KeepAliveWorker =
        KeepAliveWorker(
            context = context,
            params = workerParameters,
            aapsLogger = aapsLogger,
            fabricPrivacy = fabricPrivacy,
            localAlertUtils = localAlertUtils,
            persistenceLayer = persistenceLayer,
            config = config,
            iobCobCalculator = iobCobCalculator,
            loop = loop,
            dateUtil = dateUtil,
            activePlugin = activePlugin,
            profileFunction = profileFunction,
            rxBus = mockedRxBus,
            commandQueue = commandQueue,
            maintenance = maintenance,
            rh = rh,
            preferences = preferences,
            dstHelper = dstHelper,
            workManager = workManager,
            ch = ch
        )

    private fun resetWorkerStaticState() {
        listOf("lastRun", "lastReadStatus", "lastIobUpload").forEach { name ->
            val field = try {
                KeepAliveWorker::class.java.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                KeepAliveWorker.Companion::class.java.getDeclaredField(name)
            }
            field.isAccessible = true
            val target = if (Modifier.isStatic(field.modifiers)) null else KeepAliveWorker.Companion
            field.setLong(target, 0L)
        }
    }

    // ---- doWorkAndLog: scheduling / gating / orchestration ----------------------------------------

    @Test
    fun `doWorkAndLog reschedules the 5 and 10 minute runs when triggered by the periodic run`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(workerParameters.inputData).thenReturn(workDataOf("schedule" to KeepAliveWorker.KA_0))
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)

        // Act
        worker.doWorkAndLog()

        // Assert – the +5 min and +10 min one-time runs are enqueued
        verify(workManager, times(2)).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `doWorkAndLog runs the maintenance pipeline`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)

        // Act
        worker.doWorkAndLog()

        // Assert – the periodic housekeeping tasks are invoked
        verify(dstHelper).dstCheck()
        verify(localAlertUtils).shortenSnoozeInterval()
        verify(localAlertUtils).checkStaleBGAlert()
        verify(maintenance).deleteLogs(30)
    }

    @Test
    fun `doWorkAndLog ignores a broken schedule that fires again too soon`() = runTest {
        // Arrange – simulate a previous run "now"; a non-periodic run firing within 4 min is ignored
        worker = createWorker()
        setStaticLong("lastRun", now)

        // Act
        worker.doWorkAndLog()

        // Assert – the pipeline is skipped entirely
        verify(dstHelper, never()).dstCheck()
        verify(maintenance, never()).deleteLogs(any())
    }

    @Test
    fun `doWorkAndLog retries when the app is not yet initialized`() = runTest {
        // Arrange – init gate never completes
        worker = createWorker()
        whenever(config.appInitialized).thenReturn(false)
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))

        // Act
        val result = worker.doWorkAndLog()

        // Assert
        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
        verify(dstHelper, never()).dstCheck()
    }

    // ---- checkPump --------------------------------------------------------------------------------

    @Test
    fun `checkPump requests status when connection is outdated`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)
        testPumpPlugin.lastData = now - T.mins(20).msecs()

        // Act
        worker.checkPump()

        // Assert
        verify(commandQueue).readStatus(anyOrNull())
    }

    @Test
    fun `checkPump requests status when basal is outdated`() = runTest {
        // Arrange – connection is fresh (status not outdated) but base basal differs from requested
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)
        whenever(rh.gs(app.aaps.core.ui.R.string.keepalive_basal_outdated)).thenReturn("basal outdated")
        testPumpPlugin.lastData = now // fresh -> status not outdated; requested basal 1.0 vs pump 0.0 -> basal outdated

        // Act
        worker.checkPump()

        // Assert
        verify(commandQueue).readStatus("basal outdated")
    }

    @Test
    fun `checkPump sends profile switch event if profile is mismatched`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        testPumpPlugin.isProfileSet = false

        // Act
        worker.checkPump()

        // Assert
        verify(mockedRxBus).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump does nothing if mode is DISCONNECTED_PUMP`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.DISCONNECTED_PUMP)
        testPumpPlugin.lastData = now - T.mins(20).msecs()

        // Act
        worker.doWorkAndLog()

        // Assert
        verify(commandQueue, never()).readStatus(any())
        verify(mockedRxBus, never()).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump does nothing when there is no requested profile`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(profileFunction.getRequestedProfile()).thenReturn(null)

        // Act
        worker.checkPump()

        // Assert
        verify(commandQueue, never()).readStatus(any())
        verify(mockedRxBus, never()).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `checkPump does not request status while the pump is busy`() = runTest {
        // Arrange – outdated connection but a busy pump must not be queried
        val busyPump = mock<PumpWithConcentration>()
        whenever(busyPump.isBusy()).thenReturn(true)
        whenever(busyPump.lastDataTime).thenReturn(MutableStateFlow(now - T.mins(60).msecs()))
        whenever(busyPump.baseBasalRate).thenReturn(PumpRate(0.0))
        whenever(busyPump.pumpDescription).thenReturn(PumpDescription())
        whenever(activePlugin.activePump).thenReturn(busyPump)
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(profileFunction.getRequestedProfile()).thenReturn(profileSwitch)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)).thenReturn(true)

        // Act
        worker.checkPump()

        // Assert
        verify(commandQueue, never()).readStatus(any())
    }

    // ---- checkAPS ---------------------------------------------------------------------------------

    @Test
    fun `checkAPS schedules device status upload if BG is missing`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(ads.actualBg()).thenReturn(null)

        // Act
        worker.doWorkAndLog()

        // Assert
        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `checkAPS does not upload device status in client mode`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(config.AAPSCLIENT).thenReturn(true)

        // Act
        worker.checkAPS()

        // Assert
        verify(loop, never()).scheduleBuildAndStoreDeviceStatus(any())
    }

    @Test
    fun `checkAPS uploads device status in pump control mode`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(config.PUMPCONTROL).thenReturn(true)

        // Act
        worker.checkAPS()

        // Assert
        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    @Test
    fun `checkAPS does not upload when loop is running, BG is present and APS is recent`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(ads.actualBg()).thenReturn(mock<InMemoryGlucoseValue>())
        whenever(activePlugin.activeAPS).thenReturn(null)

        // Act
        worker.checkAPS()

        // Assert
        verify(loop, never()).scheduleBuildAndStoreDeviceStatus(any())
    }

    @Test
    fun `checkAPS uploads device status when last APS run is stale`() = runTest {
        // Arrange
        worker = createWorker()
        val aps = mock<APS>()
        whenever(aps.lastAPSRun).thenReturn(now - T.mins(10).msecs())
        whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(ads.actualBg()).thenReturn(mock<InMemoryGlucoseValue>())
        whenever(activePlugin.activeAPS).thenReturn(aps)

        // Act
        worker.checkAPS()

        // Assert
        verify(loop).scheduleBuildAndStoreDeviceStatus("KeepAliveWorker")
    }

    // ---- databaseCleanup / WorkManager housekeeping ----------------------------------------------

    @Test
    fun `databaseCleanup does NOT run if it was run less than a day ago`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.hours(12).msecs())

        // Act
        worker.doWorkAndLog()

        // Assert
        verify(persistenceLayer, never()).cleanupDatabase(any(), any())
    }

    @Test
    fun `databaseCleanup runs and records the timestamp when last run was over a day ago`() = runTest {
        // Arrange
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now - T.days(2).msecs())
        whenever(persistenceLayer.cleanupDatabase(any(), any())).thenReturn("done")

        // Act
        worker.doWorkAndLog()

        // Assert – keeps ~6 months and stamps the run time
        verify(persistenceLayer).cleanupDatabase(6 * 31, false)
        verify(preferences).put(LongNonKey.LastCleanupRun, now)
    }

    @Test
    fun `workerDbStatus prunes WorkManager DB when terminal work count is excessive`() = runTest {
        // Arrange – first query (terminal states) returns > 1000, second (active states) returns empty
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        val workInfo = mock<WorkInfo>()
        val terminal = List(1001) { workInfo }
        val bigFuture: ListenableFuture<List<WorkInfo>> = mock()
        whenever(bigFuture.get(eq(2L), eq(TimeUnit.SECONDS))).thenReturn(terminal)
        whenever(listenableFuture.get(eq(2L), eq(TimeUnit.SECONDS))).thenReturn(emptyList())
        whenever(workManager.getWorkInfos(any())).thenReturn(bigFuture, listenableFuture)

        // Act
        worker.doWorkAndLog()

        // Assert
        verify(workManager).pruneWork()
    }

    @Test
    fun `workerDbStatus survives a WorkManager query timeout without pruning`() = runTest {
        // Arrange – the bounded get() times out
        worker = createWorker()
        whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        whenever(listenableFuture.get(eq(2L), eq(TimeUnit.SECONDS))).thenThrow(TimeoutException())

        // Act
        val result = worker.doWorkAndLog()

        // Assert – no prune, the wedged future is cancelled, and the worker still completes
        verify(workManager, never()).pruneWork()
        verify(listenableFuture, atLeastOnce()).cancel(true)
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }

    private fun setStaticLong(name: String, value: Long) {
        val field = try {
            KeepAliveWorker::class.java.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            KeepAliveWorker.Companion::class.java.getDeclaredField(name)
        }
        field.isAccessible = true
        val target = if (Modifier.isStatic(field.modifiers)) null else KeepAliveWorker.Companion
        field.setLong(target, value)
    }
}
