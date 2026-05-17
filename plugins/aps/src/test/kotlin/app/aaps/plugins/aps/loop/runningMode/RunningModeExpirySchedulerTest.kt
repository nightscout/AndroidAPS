package app.aaps.plugins.aps.loop.runningMode

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
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

class RunningModeExpirySchedulerTest : TestBase() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var config: Config
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var scheduler: RunningModeExpiryScheduler
    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private val now = 1_700_000_000_000L

    @BeforeEach
    fun prepare() {
        whenever(dateUtil.now()).thenReturn(now)
        whenever(config.APS).thenReturn(true)
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        scheduler = RunningModeExpiryScheduler(
            persistenceLayer = persistenceLayer,
            workManager = workManager,
            config = config,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger,
            appScope = testScope
        )
    }

    @Test
    fun `does nothing when config APS is false`() = runTest {
        whenever(config.APS).thenReturn(false)
        val mode = permanent(RM.Mode.CLOSED_LOOP)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        scheduler.start()
        verify(workManager, never()).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
        verify(workManager, never()).cancelUniqueWork(any<String>())
    }

    @Test
    fun `permanent working mode cancels any pending scheduled work`() = runTest {
        val mode = permanent(RM.Mode.CLOSED_LOOP)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        scheduler.start()
        verify(workManager).cancelUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME))
        verify(workManager, never()).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `active temporary mode schedules worker`() = runTest {
        val duration = T.mins(30).msecs()
        val mode = temporary(RM.Mode.DISCONNECTED_PUMP, timestamp = now, durationMs = duration)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        scheduler.start()
        verify(workManager).enqueueUniqueWork(
            eq(RunningModeExpiryWorker.WORK_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            any<OneTimeWorkRequest>()
        )
    }

    @Test
    fun `already-expired temporary mode cancels rather than schedules`() = runTest {
        val duration = T.mins(30).msecs()
        val mode = temporary(RM.Mode.DISCONNECTED_PUMP, timestamp = now - T.mins(60).msecs(), durationMs = duration)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        scheduler.start()
        verify(workManager).cancelUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME))
        verify(workManager, never()).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `transition from permanent to temporary schedules work`() = runTest {
        val workingMode = permanent(RM.Mode.CLOSED_LOOP)
        val duration = T.mins(30).msecs()
        val disconnect = temporary(RM.Mode.DISCONNECTED_PUMP, timestamp = now, durationMs = duration)
        val flow = MutableSharedFlow<List<RM>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(RM::class.java))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(workingMode)

        scheduler.start()
        verify(workManager).cancelUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME))

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(disconnect)
        flow.emit(listOf(disconnect))

        verify(workManager).enqueueUniqueWork(
            eq(RunningModeExpiryWorker.WORK_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            any<OneTimeWorkRequest>()
        )
    }

    @Test
    fun `transition from temporary to permanent cancels scheduled work`() = runTest {
        val duration = T.mins(30).msecs()
        val disconnect = temporary(RM.Mode.DISCONNECTED_PUMP, timestamp = now, durationMs = duration)
        val working = permanent(RM.Mode.CLOSED_LOOP)
        val flow = MutableSharedFlow<List<RM>>(replay = 0)
        whenever(persistenceLayer.observeChanges(eq(RM::class.java))).thenReturn(flow)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(disconnect)

        scheduler.start()
        verify(workManager).enqueueUniqueWork(
            eq(RunningModeExpiryWorker.WORK_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            any<OneTimeWorkRequest>()
        )

        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(working)
        flow.emit(listOf(working))

        verify(workManager).cancelUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME))
    }

    @Test
    fun `zero-duration temporary mode cancels rather than schedules`() = runTest {
        val mode = RM(id = 99, timestamp = now, mode = RM.Mode.DISCONNECTED_PUMP, duration = 0L)
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        scheduler.start()
        verify(workManager).cancelUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME))
        verify(workManager, never()).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `Long MAX_VALUE duration sentinel cancels rather than overflowing WorkManager delay`() = runTest {
        // SUSPENDED_BY_PUMP uses Long.MAX_VALUE for "open-ended". Without guards, timestamp +
        // Long.MAX_VALUE overflows, and the double-overflow in (endTime - now) can wrap to a
        // large positive delay that WorkManager's setInitialDelay rejects with
        // IllegalArgumentException. The scheduler must short-circuit these instead.
        val mode = RM(
            id = 99, timestamp = now,
            mode = RM.Mode.SUSPENDED_BY_PUMP, duration = Long.MAX_VALUE
        )
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        scheduler.start()
        verify(workManager).cancelUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME))
        verify(workManager, never()).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun `near-overflow duration that wraps timestamp + duration cancels rather than schedules`() = runTest {
        // Any duration big enough that timestamp + duration overflows the Long range must be
        // treated as open-ended, not turned into an overflowed delay.
        val mode = RM(
            id = 99, timestamp = now,
            mode = RM.Mode.DISCONNECTED_PUMP, duration = Long.MAX_VALUE - 1_000_000L
        )
        whenever(persistenceLayer.getRunningModeActiveAt(anyLong())).thenReturn(mode)
        scheduler.start()
        verify(workManager).cancelUniqueWork(eq(RunningModeExpiryWorker.WORK_NAME))
        verify(workManager, never()).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
    }

    // --- Helpers ---

    private fun permanent(mode: RM.Mode) = RM(id = mode.ordinal.toLong() + 1, timestamp = now, mode = mode, duration = 0L)

    private fun temporary(mode: RM.Mode, timestamp: Long, durationMs: Long) =
        RM(id = mode.ordinal.toLong() + 100, timestamp = timestamp, mode = mode, duration = durationMs)
}
