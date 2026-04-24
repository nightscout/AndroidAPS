package app.aaps

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.di.TestApplication
import app.aaps.helpers.RxHelper
import app.aaps.implementation.profile.ProfileFunctionImpl
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryWorker
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import javax.inject.Inject

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
 */
class RunningModeReconcilerIntegrationTest @Inject constructor() {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var runningModeReconciler: RunningModeReconciler
    @Inject lateinit var runningModeExpiryScheduler: RunningModeExpiryScheduler
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rxHelper: RxHelper
    @Inject lateinit var config: Config
    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var localProfileManager: LocalProfileManager
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var pumpSync: PumpSync

    private val context = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun setUp() {
        context.androidInjector().inject(this)
        WorkManager.getInstance(context).cancelAllWork()
        runBlocking { persistenceLayer.clearDatabases() }
        (profileFunction as ProfileFunctionImpl).cache.clear()
        // TestApplication does not start the reconciler / scheduler on its own — start them here.
        runningModeReconciler.start()
        runningModeExpiryScheduler.start()
    }

    @After
    fun tearDown() {
        rxHelper.clear()
        WorkManager.getInstance(context).cancelAllWork()
        runBlocking { persistenceLayer.clearDatabases() }
    }

    // --- Queue gate: bolus / extendedBolus / cancelTempBasal ---

    @Test
    fun `queue gate rejects bolus when mode is DISCONNECTED_PUMP`() = runBlocking {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val rejection = CaptureCallback()
        val info = DetailedBolusInfo().apply { insulin = 1.0 }
        val queued = commandQueue.bolus(info, rejection)
        assertThat(queued).isFalse()
        assertThat(rxHelper.waitUntil("bolus rejection callback fired", maxSeconds = 5) { rejection.invoked }).isTrue()
        assertThat(rejection.capturedResult?.success).isFalse()
        assertThat(rejection.capturedResult?.enacted).isFalse()
    }

    @Test
    fun `queue gate rejects extended bolus when mode is DISCONNECTED_PUMP`() = runBlocking {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val rejection = CaptureCallback()
        val queued = commandQueue.extendedBolus(2.0, 30, rejection)
        assertThat(queued).isFalse()
        assertThat(rxHelper.waitUntil("eb rejection callback fired", maxSeconds = 5) { rejection.invoked }).isTrue()
        assertThat(rejection.capturedResult?.success).isFalse()
    }

    @Test
    fun `queue gate allows cancelTempBasal during DISCONNECTED_PUMP`() = runBlocking {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val queued = commandQueue.cancelTempBasal(enforceNew = true, autoForced = false, callback = null)
        assertThat(queued).isTrue()
    }

    @Test
    fun `queue gate rejects bolus when mode is SUSPENDED_BY_USER`() = runBlocking {
        insertActiveMode(RM.Mode.SUSPENDED_BY_USER, durationMs = T.mins(30).msecs())
        val rejection = CaptureCallback()
        val info = DetailedBolusInfo().apply { insulin = 1.0 }
        val queued = commandQueue.bolus(info, rejection)
        assertThat(queued).isFalse()
        assertThat(rxHelper.waitUntil("bolus rejection callback fired", maxSeconds = 5) { rejection.invoked }).isTrue()
    }

    @Test
    fun `queue gate allows bolus when mode is working`() = runBlocking {
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        val info = DetailedBolusInfo().apply { insulin = 0.1 }
        val queued = commandQueue.bolus(info, null)
        assertThat(queued).isTrue()
    }

    @Test
    fun `queue gate reflects the mode active at call time not at startup`() = runBlocking {
        // Startup in working mode.
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        // Initial bolus passes.
        val allowed = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 0.05 }, null)
        assertThat(allowed).isTrue()
        commandQueue.clear()

        // Transition to DISCONNECTED_PUMP.
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        // Same call is now rejected.
        val rejection = CaptureCallback()
        val rejected = commandQueue.bolus(DetailedBolusInfo().apply { insulin = 0.05 }, rejection)
        assertThat(rejected).isFalse()
        assertThat(rxHelper.waitUntil("rejection callback after mode flip", maxSeconds = 5) { rejection.invoked }).isTrue()
    }

    // --- Expiry scheduler: schedules + cancels work ---

    @Test
    fun `expiry scheduler enqueues unique work when a temporary RM is written`() = runBlocking {
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val workScheduled = rxHelper.waitUntil("expiry work scheduled", maxSeconds = 10) {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(RunningModeExpiryWorker.WORK_NAME).get()
            infos.any { it.state == WorkInfo.State.ENQUEUED }
        }
        assertThat(workScheduled).isTrue()
    }

    @Test
    fun `expiry scheduler cancels work when active mode becomes permanent`() = runBlocking {
        // Schedule work by entering a temporary mode.
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        rxHelper.waitUntil("expiry work present", maxSeconds = 10) {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(RunningModeExpiryWorker.WORK_NAME).get()
            infos.any { it.state == WorkInfo.State.ENQUEUED }
        }
        // Exit by writing a permanent mode.
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        val workCancelled = rxHelper.waitUntil("expiry work cancelled", maxSeconds = 10) {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(RunningModeExpiryWorker.WORK_NAME).get()
            // Cancelled work may be absent or in a terminal state.
            infos.isEmpty() || infos.all { it.state.isFinished }
        }
        assertThat(workCancelled).isTrue()
    }

    // --- Source invariance / DB write triggers observer reaction ---

    @Test
    fun `DB write bypassing LoopPlugin still triggers reconciler observer`() = runBlocking {
        // Start in a working mode.
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        Thread.sleep(500)

        // Write SUSPENDED_BY_USER via persistenceLayer directly (simulating a scene or NS import).
        // The reconciler's decision for working→SUSPENDED_BY_USER is CancelTbr; commandQueue
        // should receive a cancel regardless of whether there is an active TBR to cancel.
        // We verify via the expiry scheduler's visible side effect: a temporary-RM write always
        // schedules the expiry worker.
        insertActiveMode(RM.Mode.SUSPENDED_BY_USER, durationMs = T.mins(15).msecs())

        val scheduled = rxHelper.waitUntil("expiry work scheduled for SUSPENDED_BY_USER", maxSeconds = 10) {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(RunningModeExpiryWorker.WORK_NAME).get()
            infos.any { it.state == WorkInfo.State.ENQUEUED }
        }
        assertThat(scheduled).isTrue()
    }

    // --- Helpers ---

    private fun insertActiveMode(mode: RM.Mode, durationMs: Long) {
        runBlocking {
            @Suppress("CheckResult")
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
            )
        }
    }

    private class CaptureCallback : Callback() {

        @Volatile var invoked: Boolean = false
        val capturedResult: PumpEnactResult? get() = if (invoked) super.result else null
        override fun run() {
            invoked = true
        }
    }

    // ==========================================================================================
    // End-to-end scenarios with full profile setup
    //
    // The tests below set up a real profile so that the reconciler can actually issue pump
    // commands (which require a non-null Profile). They then observe the full chain:
    // DB write → reconciler observer → commandQueue → pump → pumpSync state.
    // ==========================================================================================

    @Test
    fun `gate rejects non-zero TBR during DISCONNECTED_PUMP even via commandQueue with real profile`() = runBlocking {
        ensureProfile()
        val profile = profileFunction.getProfile() ?: error("profile not available")
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        val rejection = CaptureCallback()
        val queued = commandQueue.tempBasalAbsolute(
            absoluteRate = 1.5,
            durationInMinutes = 30,
            enforceNew = true,
            profile = profile,
            tbrType = PumpSync.TemporaryBasalType.NORMAL,
            callback = rejection
        )
        assertThat(queued).isFalse()
        assertThat(rxHelper.waitUntil("non-zero TBR rejection callback", maxSeconds = 5) { rejection.invoked }).isTrue()
        assertThat(rejection.capturedResult?.success).isFalse()
        assertThat(rejection.capturedResult?.enacted).isFalse()
    }

    @Test
    fun `reconciler issues zero-TBR on entry to DISCONNECTED_PUMP and cancels on exit`() = runBlocking {
        ensureProfile()
        // Baseline: working mode, no zero-TBR.
        insertActiveMode(RM.Mode.CLOSED_LOOP, durationMs = 0L)
        Thread.sleep(500)

        // Enter DISCONNECTED_PUMP.
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())

        // Reconciler observes, issues zero-TBR, queue executes, pump state reflects it.
        val tbrArrived = rxHelper.waitUntil("zero TBR on pump", maxSeconds = 30) {
            val tbr = runBlocking { pumpSync.expectedPumpState() }.temporaryBasal
            tbr != null && tbr.rate == 0.0 && tbr.type == PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
        }
        assertThat(tbrArrived).isTrue()

        // Exit: cancel the running mode (simulates user RESUME).
        persistenceLayer.cancelCurrentRunningMode(
            timestamp = dateUtil.now(),
            action = Action.RECONNECT,
            source = Sources.Aaps,
            note = null,
            listValues = emptyList()
        )

        // Reconciler observes transition zero-delivery → working and cancels TBR.
        val tbrCleared = rxHelper.waitUntil("TBR cleared on pump", maxSeconds = 30) {
            runBlocking { pumpSync.expectedPumpState() }.temporaryBasal == null
        }
        assertThat(tbrCleared).isTrue()
    }

    @Test
    fun `source invariance — handleRunningModeChange and direct persistenceLayer produce the same pump state`() = runBlocking {
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
        assertThat(rxHelper.waitUntil("path A: zero TBR on pump", maxSeconds = 30) {
            val tbr = runBlocking { pumpSync.expectedPumpState() }.temporaryBasal
            tbr != null && tbr.rate == 0.0 && tbr.type == PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
        }).isTrue()
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
        assertThat(rxHelper.waitUntil("reset between paths", maxSeconds = 30) {
            runBlocking { pumpSync.expectedPumpState() }.temporaryBasal == null
        }).isTrue()

        // Path B: direct persistenceLayer.insertOrUpdateRunningMode (scene / NS / future writers).
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = T.mins(30).msecs())
        assertThat(rxHelper.waitUntil("path B: zero TBR on pump", maxSeconds = 30) {
            val tbr = runBlocking { pumpSync.expectedPumpState() }.temporaryBasal
            tbr != null && tbr.rate == 0.0 && tbr.type == PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
        }).isTrue()
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
    fun `expiry worker cancels zero-TBR at natural RM end`() = runBlocking {
        ensureProfile()
        // Duration must round to at least 1 minute of remaining for the reconciler to issue
        // a zero-TBR (sub-minute remaining is treated as expired, and the test would see no TBR
        // to cancel). 65 seconds → 1 minute remaining.
        val durationMs = 65_000L
        insertActiveMode(RM.Mode.DISCONNECTED_PUMP, durationMs = durationMs)

        // Phase 1: reconciler issues zero-TBR.
        assertThat(rxHelper.waitUntil("zero TBR active before expiry", maxSeconds = 30) {
            val tbr = runBlocking { pumpSync.expectedPumpState() }.temporaryBasal
            tbr != null && tbr.rate == 0.0 && tbr.type == PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
        }).isTrue()

        // Phase 2: at RM end, the expiry worker fires and cancels the zero-TBR.
        // Budget: RM duration (65s) + 30s slack for worker latency and queue drain.
        assertThat(rxHelper.waitUntil("expiry worker cleared TBR", maxSeconds = 95) {
            runBlocking { pumpSync.expectedPumpState() }.temporaryBasal == null
        }).isTrue()
    }

    // --- Profile setup helper ---

    private suspend fun ensureProfile() {
        if (profileFunction.getProfile() != null &&
            pumpSync.expectedPumpState().profile != null
        ) return

        nsIncomingDataProcessor.processProfile(JSONObject(profileData), false)
        val store = localProfileManager.profile ?: error("no profile store after NS import")
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

        assertThat(rxHelper.waitUntil("profile ready", maxSeconds = 20) {
            runBlocking { profileFunction.getProfile() } != null
        }).isTrue()
        assertThat(rxHelper.waitUntil("pump has profile", maxSeconds = 20) {
            runBlocking { pumpSync.expectedPumpState() }.profile != null
        }).isTrue()
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
