package app.aaps.implementation.receivers

import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.dst.DstHelper
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.LongNonKey
import app.aaps.implementation.maintenance.KeepAliveRunner
import app.aaps.implementation.maintenance.PeriodicMaintenance
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * What only Android has around the keep-alive checks: the WorkManager schedule and the WorkManager database checks.
 * The checks themselves are in `KeepAliveRunnerTest`.
 */
class KeepAliveWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: KeepAliveWorker
    private lateinit var runner: KeepAliveRunner

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
        whenever(iobCobCalculator.ads).thenReturn(ads)
        whenever(workManager.getWorkInfos(any())).thenReturn(listenableFuture)
        whenever(listenableFuture.get()).thenReturn(emptyList())
        whenever(workerParameters.inputData).thenReturn(workDataOf("schedule" to "KA_5"))
        // Short-circuit Config.awaitInitialized() so the pass proceeds past the init gate.
        // Without this the suspend gate hits initProgressFlow (null on a fresh Mock) and NPEs.
        whenever(config.appInitialized).thenReturn(true)
        // Default: cleanup ran "now" so the database cleanup is a no-op.
        whenever(preferences.get(LongNonKey.LastCleanupRun)).thenReturn(now)
        runBlocking { whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP) }
        // The real runner, so that the worker is tested with the checks it really runs.
        runner = KeepAliveRunner(
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
            periodicMaintenance = PeriodicMaintenance(aapsLogger, localAlertUtils, persistenceLayer, maintenance, preferences, dateUtil),
            rh = baseText,
            dstHelper = dstHelper,
            ch = ch
        )
        worker = KeepAliveWorker(
            context = context,
            params = workerParameters,
            aapsLogger = aapsLogger,
            fabricPrivacy = fabricPrivacy,
            keepAliveRunner = runner,
            workManager = workManager
        )
    }

    @Test
    fun `the periodic run schedules the runs at 5 and 10 minutes`() = runTest {
        whenever(workerParameters.inputData).thenReturn(workDataOf("schedule" to KeepAliveWorker.KA_0))

        worker.doWorkAndLog()

        verify(workManager, times(2)).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `a run at 5 or 10 minutes schedules nothing`() = runTest {
        worker.doWorkAndLog()

        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `a run runs the keep-alive checks`() = runTest {
        val result = worker.doWorkAndLog()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        verify(dstHelper).dstCheck()
        verify(loop).verifyZeroDelivery()
        verify(maintenance).deleteLogs(30)
    }

    @Test
    fun `a broken schedule that fires a 5 or 10 minute run too soon is ignored`() = runTest {
        runner.lastRun = now

        worker.doWorkAndLog()

        verify(dstHelper, never()).dstCheck()
        verify(maintenance, never()).deleteLogs(any())
        verify(workManager, never()).getWorkInfos(any())
    }

    @Test
    fun `the periodic run is not ignored also right after the last pass`() = runTest {
        whenever(workerParameters.inputData).thenReturn(workDataOf("schedule" to KeepAliveWorker.KA_0))
        runner.lastRun = now

        worker.doWorkAndLog()

        verify(dstHelper).dstCheck()
    }

    @Test
    fun `the worker retries when the app is not yet initialized`() = runTest {
        whenever(config.appInitialized).thenReturn(false)
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress()))

        val result = worker.doWorkAndLog()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
        verify(dstHelper, never()).dstCheck()
    }

    @Test
    fun `workerDbStatus prunes WorkManager DB when terminal work count is excessive`() = runTest {
        // First query (terminal states) returns > 1000, second (active states) returns empty
        val workInfo = mock<WorkInfo>()
        val terminal = List(1001) { workInfo }
        val bigFuture: ListenableFuture<List<WorkInfo>> = mock()
        whenever(bigFuture.get(eq(2L), eq(TimeUnit.SECONDS))).thenReturn(terminal)
        whenever(listenableFuture.get(eq(2L), eq(TimeUnit.SECONDS))).thenReturn(emptyList())
        whenever(workManager.getWorkInfos(any())).thenReturn(bigFuture, listenableFuture)

        worker.doWorkAndLog()

        verify(workManager).pruneWork()
    }

    @Test
    fun `workerDbStatus survives a WorkManager query timeout without pruning`() = runTest {
        // The bounded get() times out
        whenever(listenableFuture.get(eq(2L), eq(TimeUnit.SECONDS))).thenThrow(TimeoutException())

        val result = worker.doWorkAndLog()

        // No prune, the wedged future is cancelled, and the worker still completes
        verify(workManager, never()).pruneWork()
        verify(listenableFuture, atLeastOnce()).cancel(true)
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
}
