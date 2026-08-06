package app.aaps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.helpers.IntegrationWaits
import app.aaps.helpers.RxHelper
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryWorker
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import app.aaps.testcategories.ShardB
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end integration scenarios for the running-mode reconciliation pipeline.
 *
 * Focuses on the plumbing that only emerges when everything is assembled:
 *  - the DB→observer→reconciler chain reacts to writes that bypass LoopPlugin
 *  - the queue-level gate rejects commands based on the *current* active mode read at the
 *    point of the call (not on a cached copy)
 *  - the expiry scheduler actually enqueues / cancels work with real WorkManager
 *
 * The pure logic (transition table, gate predicate, duration rounding) is exhaustively covered
 * by the JVM unit tests in core:objects and plugins:aps.
 *
 * ### Waiting
 * Every wait here goes through [IntegrationWaits], never `Thread.sleep` or `RxHelper.waitUntil`: both
 * of those block the very thread `runTest` drives, and `waitUntil` reports a timeout by returning
 * `false` into an `isTrue()` assertion whose whole message is "expected to be true" — useless in a CI
 * log. Each test also carries an explicit `timeout`, because `runTest` defaults to 60s while the wait
 * budgets below legitimately exceed that on a loaded box (CI build 40565 turned the whole job red this
 * way, on a commons-codec version bump, and the identical SHA passed in 40566).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ShardB
class RunningModeReconcilerIntegrationTest : HiltInstrumentedTest() {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var runningModeReconciler: RunningModeReconciler
    @Inject lateinit var runningModeExpiryScheduler: RunningModeExpiryScheduler
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var integrationWaits: IntegrationWaits
    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var pumpSync: PumpSync

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * Wall-clock budget for a whole test method.
     *
     * `runTest`'s default is 60s, but the waits inside a single test legitimately add up past that:
     * [ensureProfile] alone budgets 40s + 60s, and a zero-TBR round trip budgets another 60s. Exceeding
     * the default aborts the test from the coroutine machinery, producing a stack of nothing but
     * `kotlinx.coroutines.test` frames — which is what made the original CI failure so hard to read.
     */
    private val testTimeout = 5.minutes

    @Before
    fun setUp() {
        WorkManager.getInstance(context).cancelAllWork()
        runBlocking { persistenceLayer.clearDatabases() }
        (profileFunction as ProfileFunctionImpl).cache.clear()
        // The reconciler is a process-wide @Singleton shared across all tests; clear its stale
        // in-memory dedup baseline and observer coroutine (from the previous test) so start()
        // below re-baselines against the just-wiped DB. Without this, a freshly inserted mode can
        // collide with the previous test's baseline and be de-duplicated, so no pump action fires.
        runningModeReconciler.resetState()
        // The test application does not start the reconciler / scheduler on its own — start them here.
        runningModeReconciler.start()
        runningModeExpiryScheduler.start()
        runBlocking {
            // start() only *launches* the reconcile; the change observer subscribes after the startup
            // reconcile completes. Wait for that instead of guessing, so a mode written by the test
            // body cannot land in the window where nothing is subscribed and be dropped (the change
            // flow has no replay, so a missed emission is gone for good).
            integrationWaits.awaitCondition("reconciler startup reconcile", timeoutMs = 30_000) {
                runningModeReconciler.reconciledMode() != null
            }
            // Drain late-arriving commands from the previous test's appScope coroutines (e.g. the
            // reconciler is still inside commandQueue.tempBasalPercent when tearDown clears the queue,
            // and the `add()` lands after the clear). Wait for the queue to be *sustainably* empty —
            // a single empty sample proves nothing, since the late add may simply not have happened
            // yet — then clear once more.
            integrationWaits.awaitQuiet("command queue after previous test", quietMs = 300, timeoutMs = 10_000) {
                commandQueue.size() > 0
            }
        }
        commandQueue.clear()
    }

    @After
    fun tearDown() {
        rxHelper.clear()
        WorkManager.getInstance(context).cancelAllWork()
        // Reset queue state: `performing` is a singleton field and survives WorkManager.cancelAllWork().
        // Without this, a long-running command (e.g. a virtual-pump bolus simulating delivery via
        // SystemClock.sleep) leaves `performing != null`, and the next test's QueueWorker spins.
        commandQueue.clear()
        runBlocking { persistenceLayer.clearDatabases() }
    }

    // --- Queue gate: bolus / extendedBolus / cancelTempBasal ---

    @Test
    fun `queue gate rejects bolus when mode is DISCONNECTED_PUMP`() = runTest(timeout = testTimeout) {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val info = DetailedBolusInfo().apply { insulin = 1.0 }
        val result = commandQueue.bolus(info)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `queue gate rejects extended bolus when mode is DISCONNECTED_PUMP`() = runTest(timeout = testTimeout) {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val result = commandQueue.extendedBolus(2.0, 30)
        assertThat(result.success).isFalse()
    }

    @Test
    fun `queue gate allows cancelTempBasal during DISCONNECTED_PUMP`() = runTest(timeout = testTimeout) {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        backgroundScope.launch { commandQueue.cancelTempBasal(enforceNew = true, autoForced = false) }
        yield()
        assertThat(commandQueue.size()).isGreaterThan(0)
    }

    @Test
    fun `queue gate allows bolus when mode is working`() = runTest(timeout = testTimeout) {
        ensureProfile()
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        // bolus() is a suspend function that blocks until pump delivery completes.
        // Gate in CLOSED_LOOP allows the command; VirtualPump delivers successfully.
        val result = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 0.1 })
        assertThat(result.success).isTrue()
    }

    @Test
    fun `queue gate reflects the mode active at call time not at startup`() = runTest(timeout = testTimeout) {
        ensureProfile()
        // Startup in working mode.
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        // Gate reads mode at call time — must allow in CLOSED_LOOP and complete successfully.
        val firstResult = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 0.1 })
        assertThat(firstResult.success).isTrue()

        // Transition to DISCONNECTED_PUMP.
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        // Same call is now rejected at the gate.
        val result = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 0.1 })
        assertThat(result.success).isFalse()
    }

    // --- Expiry scheduler: schedules + cancels work ---

    @Test
    fun `expiry scheduler enqueues unique work when a temporary RM is written`() = runTest(timeout = testTimeout) {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        integrationWaits.awaitCondition("expiry work scheduled", timeoutMs = 30_000) { expiryWorkEnqueued() }
    }

    @Test
    fun `expiry scheduler cancels work when active mode becomes permanent`() = runTest(timeout = testTimeout) {
        // Schedule work by entering a temporary mode.
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        integrationWaits.awaitCondition("expiry work present", timeoutMs = 30_000) { expiryWorkEnqueued() }
        // Exit by writing a permanent mode.
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        integrationWaits.awaitCondition("expiry work cancelled", timeoutMs = 30_000) {
            // Cancelled work may be absent or in a terminal state.
            val infos = expiryWorkInfos()
            infos.isEmpty() || infos.all { it.state.isFinished }
        }
    }

    // --- Source invariance / DB write triggers observer reaction ---

    @Test
    fun `DB write bypassing LoopPlugin still triggers reconciler observer`() = runTest(timeout = testTimeout) {
        // Start in a working mode, and wait until the reconciler has actually consumed it — the
        // transition under test is working→SUSPENDED_BY_USER, so the baseline must be reconciled
        // first or the two writes collapse into a single observed change.
        awaitBaseline(RM.Mode.CLOSED_LOOP)

        // Write SUSPENDED_BY_USER via persistenceLayer directly (simulating a scene or NS import).
        // The reconciler's decision for working→SUSPENDED_BY_USER is CancelTbr; commandQueue
        // should receive a cancel regardless of whether there is an active TBR to cancel.
        // We verify via the expiry scheduler's visible side effect: a temporary-RM write always
        // schedules the expiry worker.
        insertActiveMode(RM.Mode.SUSPENDED_BY_USER, durationMs = T.mins(15).msecs())

        integrationWaits.awaitCondition("expiry work scheduled for SUSPENDED_BY_USER", timeoutMs = 30_000) {
            expiryWorkEnqueued()
        }
    }

    // --- Helpers ---

    /** Insert [mode] as the active running mode and return the id of the written row. */
    private suspend fun insertActiveMode(mode: RM.Mode, durationMs: Long): Long? =
        persistenceLayer.insertOrUpdateRunningMode(
            runningMode = RM(
                timestamp = dateUtil.now(),
                mode = mode,
                autoForced = false,
                duration = durationMs
            ),
            action = Action.CLOSED_LOOP_MODE,
            source = Sources.Aaps,
            listValues = listOf(ValueWithUnit.SimpleString("IntegrationTest"))
        ).all().firstOrNull()?.id

    /**
     * Write [mode] as the baseline and suspend until the reconciler has reconciled *that row*.
     *
     * Deterministic replacement for "insert, then sleep and hope the observer got there": the wait ends
     * exactly when the reconciler's own bookkeeping names the inserted row, which proves both that the
     * collector is subscribed and that this change — not a later one — was the one it acted on.
     */
    private suspend fun awaitBaseline(mode: RM.Mode) {
        val baselineId = insertActiveMode(mode, durationMs = 0L)
        integrationWaits.awaitCondition("reconciler baselined on $mode", timeoutMs = 30_000) {
            runningModeReconciler.reconciledRowId() == baselineId && runningModeReconciler.reconciledMode() == mode
        }
    }

    private fun expiryWorkInfos(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(RunningModeExpiryWorker.WORK_NAME).get()

    private fun expiryWorkEnqueued(): Boolean = expiryWorkInfos().any { it.state == WorkInfo.State.ENQUEUED }

    private suspend fun zeroTbrOnPump(): Boolean {
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        return tbr != null && tbr.rate == 0.0 && tbr.type == PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
    }

    private suspend fun noTbrOnPump(): Boolean = pumpSync.expectedPumpState().temporaryBasal == null

    // ==========================================================================================
    // End-to-end scenarios with full profile setup
    //
    // The tests below set up a real profile so that the reconciler can actually issue pump
    // commands (which require a non-null Profile). They then observe the full chain:
    // DB write → reconciler observer → commandQueue → pump → pumpSync state.
    // ==========================================================================================

    @Test
    fun `gate rejects non-zero TBR during DISCONNECTED_PUMP even via commandQueue with real profile`() = runTest(timeout = testTimeout) {
        ensureProfile()
        val profile = profileFunction.getProfile() ?: error("profile not available")
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val result = commandQueue.tempBasalAbsolute(
            absoluteRate = 1.5,
            durationInMinutes = 30,
            enforceNew = true,
            profile = profile,
            tbrType = PumpSync.TemporaryBasalType.NORMAL
        )
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `reconciler issues zero-TBR on entry to DISCONNECTED_PUMP and cancels on exit`() = runTest(timeout = testTimeout) {
        ensureProfile()
        // Baseline: working mode, no zero-TBR. Wait for the reconciler to consume it, so the
        // DISCONNECTED_PUMP write below is seen as a *transition* out of a working mode.
        awaitBaseline(RM.Mode.CLOSED_LOOP)

        // Enter DISCONNECTED_PUMP.
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())

        // Reconciler observes, issues zero-TBR, queue executes, pump state reflects it.
        integrationWaits.awaitCondition("zero TBR on pump", timeoutMs = 60_000) { zeroTbrOnPump() }

        // Exit: cancel the running mode (simulates user RESUME).
        persistenceLayer.cancelCurrentRunningMode(
            timestamp = dateUtil.now(),
            action = Action.RECONNECT,
            source = Sources.Aaps,
            note = null,
            listValues = emptyList()
        )

        // Reconciler observes transition zero-delivery → working and cancels TBR.
        integrationWaits.awaitCondition("TBR cleared on pump", timeoutMs = 60_000) { noTbrOnPump() }
    }

    @Test
    fun `source invariance - handleRunningModeChange and direct persistenceLayer produce the same pump state`() = runTest(timeout = testTimeout) {
        ensureProfile()
        val profile = profileFunction.getProfile() ?: error("profile not available")

        // Path A: via LoopPlugin.handleRunningModeChange (the existing user-facing API).
        loop.handleRunningModeChange(
            durationInMinutes = 30,
            profile = profile,
            newRM = RM.Mode.DISCONNECTED_PUMP,
            action = Action.DISCONNECT,
            source = Sources.Aaps,
            listValues = emptyList()
        )
        integrationWaits.awaitCondition("path A: zero TBR on pump", timeoutMs = 60_000) { zeroTbrOnPump() }
        val pathAState = pumpSync.expectedPumpState()
        val pathATbrRate = pathAState.temporaryBasal?.rate

        // Reset: cancel current mode, wait for TBR to clear.
        persistenceLayer.cancelCurrentRunningMode(
            timestamp = dateUtil.now(),
            action = Action.RECONNECT,
            source = Sources.Aaps,
            note = null,
            listValues = emptyList()
        )
        integrationWaits.awaitCondition("reset between paths", timeoutMs = 60_000) { noTbrOnPump() }

        // Path B: direct persistenceLayer.insertOrUpdateRunningMode (scene / NS / future writers).
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        integrationWaits.awaitCondition("path B: zero TBR on pump", timeoutMs = 60_000) { zeroTbrOnPump() }
        val pathBState = pumpSync.expectedPumpState()
        val pathBTbrRate = pathBState.temporaryBasal?.rate

        // Both paths must produce the same pump outcome: zero-TBR with EMULATED_PUMP_SUSPEND type.
        assertThat(pathATbrRate).isEqualTo(pathBTbrRate)
        assertThat(pathAState.temporaryBasal?.type).isEqualTo(pathBState.temporaryBasal?.type)
    }

    /**
     * End-to-end expiry verification. Disabled by default: waits 65+ seconds for a real
     * WorkManager-scheduled job to fire, which would dominate CI wall-clock.
     *
     * Verified passing in a manual run (see commit history / test run on emulator).
     * Run locally on demand with:
     *   ./gradlew.bat :app:connectedFullDebugAndroidTest \
     *     -Pandroid.testInstrumentationRunnerArguments.class=app.aaps.RunningModeReconcilerIntegrationTest#expiry_worker_cancels_zero-TBR_at_natural_RM_end
     *
     * The worker-scheduling logic is covered by [RunningModeExpirySchedulerTest] (JVM unit) and
     * the two `expiry scheduler …` tests above (schedule + cancel on transition).
     */
    @Test
    @Ignore("Slow (65s+): real-time wait for WorkManager expiry. Verified manually; keep out of CI.")
    fun `expiry worker cancels zero-TBR at natural RM end`() = runTest(timeout = testTimeout) {
        ensureProfile()
        // Duration must round to at least 1 minute of remaining for the reconciler to issue
        // a zero-TBR (sub-minute remaining is treated as expired, and the test would see no TBR
        // to cancel). 65 seconds → 1 minute remaining.
        val durationMs = 65_000L
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = durationMs)

        // Phase 1: reconciler issues zero-TBR.
        integrationWaits.awaitCondition("zero TBR active before expiry", timeoutMs = 60_000) { zeroTbrOnPump() }

        // Phase 2: at RM end, the expiry worker fires and cancels the zero-TBR.
        // Budget: RM duration (65s) + 30s slack for worker latency and queue drain.
        integrationWaits.awaitCondition("expiry worker cleared TBR", timeoutMs = 95_000) { noTbrOnPump() }
    }

    // --- Profile setup helper ---

    private suspend fun ensureProfile() {
        if (profileFunction.getProfile() != null &&
            pumpSync.expectedPumpState().profile != null
        ) return

        nsIncomingDataProcessor.processProfile(JSONObject(profileData), true)
        val store = profileRepository.profile.value ?: error("no profile store after NS import")
        val defaultName = store.getDefaultProfileName() ?: error("no default profile name")
        profileFunction.createProfileSwitch(
            profileStore = store,
            profileName = defaultName,
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = Sources.Aaps,
            note = "integration test setup",
            listValues = listOf(
                ValueWithUnit.SimpleString(defaultName),
                ValueWithUnit.Percent(100)
            ),
            iCfg = ICfg("Test", insulinEndTime = 5 * 3600 * 1000L, insulinPeakTime = 75 * 60 * 1000L)
        ) ?: error("createProfileSwitch returned null")

        integrationWaits.awaitCondition("profile ready", timeoutMs = 40_000) { profileFunction.getProfile() != null }
        integrationWaits.awaitCondition("pump has profile", timeoutMs = 60_000) { pumpSync.expectedPumpState().profile != null }
    }

    companion object {

        // Minimal NS profile JSON (adapted from LoopTest). Real values — not a stub — so
        // commandQueue.applyBasalConstraints and the pump accept a zero-TBR against this profile.
        private const val profileData =
            "{\"_id\":\"653f90bc89f99714b4635b33\",\"defaultProfile\":\"U200_32\",\"date\":1695655201449,\"created_at\":\"2023-09-25T15:20:01.449Z\"," +
                "\"startDate\":\"2023-09-25T15:20:01.4490000Z\",\"store\":{\"U200_32\":{\"dia\":8,\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":10.0}]," +
                "\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":8.55}]," +
                "\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.5}]," +
                "\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}]," +
                "\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.5}]," +
                "\"units\":\"mmol\",\"timezone\":\"GMT\"}}," +
                "\"app\":\"AAPS\",\"utcOffset\":120,\"identifier\":\"6b503f6c-b676-5746-b331-658b03d50843\"," +
                "\"srvModified\":1698763282534,\"srvCreated\":1698664636986,\"subject\":\"Phone\"}"
    }
}
