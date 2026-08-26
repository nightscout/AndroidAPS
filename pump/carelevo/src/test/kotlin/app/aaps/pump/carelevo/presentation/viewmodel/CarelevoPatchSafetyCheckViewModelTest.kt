package app.aaps.pump.carelevo.presentation.viewmodel

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.command.CarelevoActivationExecutor
import app.aaps.pump.carelevo.command.CmdAdditionalPriming
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.command.CmdSafetyCheck
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResult
import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResultModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.type.SafetyProgress
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectSafetyCheckEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoOverviewEvent
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * JVM (non-Robolectric) unit tests for [CarelevoPatchSafetyCheckViewModel].
 *
 * The VM needs no Android framework surface — every collaborator is an interface/class that Mockito
 * can satisfy, and the only Android-ish dependency is `viewModelScope`, which resolves through
 * `Dispatchers.Main.immediate`. Installing [Dispatchers.Unconfined] as Main therefore makes the whole
 * VM testable on a plain JVM, and makes every `viewModelScope.launch` (the event emits, the
 * `setUiState` hops, the safety-check/discard/priming bodies) run EAGERLY and synchronously inside
 * the call that triggered them. That is why the tests can assert StateFlow values straight after
 * calling a VM method, with no `runTest`/`advanceUntilIdle`.
 *
 * Unconfined — NOT `UnconfinedTestDispatcher` — is required: the test dispatcher enters a coroutine
 * eagerly but queues every RESUMPTION on its TestCoroutineScheduler, so the progress collector would
 * subscribe, park, and never be resumed by an emit before `progressJob.cancel()` runs. Unconfined
 * resumes inline on the emitting thread, which is what makes the progress-frame tests deterministic.
 *
 * `commandQueue.customCommand` is a suspend fun; it is stubbed via [stubCustomCommand] (a
 * `runBlocking` wrapper around the normal `whenever`, matching `CarelevoPumpPluginStatusTest`) and
 * verified with `verifyBlocking`.
 *
 * Rx schedulers are stubbed to [Schedulers.trampoline] so the force-discard `Single` runs
 * synchronously on the test thread.
 *
 * The countdown ticker is the one thing the injected [AapsSchedulers] does NOT reach: the VM builds it
 * with the 5-arg `Observable.intervalRange`, whose overload hard-wires `Schedulers.computation()` (only
 * the `observeOn` hop is injected). It is virtualised instead through
 * [RxJavaPlugins.setComputationSchedulerHandler], which swaps in a [TestScheduler] — `Schedulers
 * .computation()` routes through that plugin hook on every call, so `intervalRange` picks it up. Ticks
 * then fire only on an explicit [TestScheduler.triggerActions]/[TestScheduler.advanceTimeBy], and the
 * `observeOn(trampoline)` hop redelivers them in-line on the advancing thread — so a tick is fully
 * applied to the StateFlows by the time `advanceTimeBy` returns.
 *
 * That is also why the ticker tests drive time from INSIDE the `customCommand` answer (see
 * [stubCustomCommandEmitting]): that is the only window in which the ticker is alive, since the
 * terminal block disposes it before writing the final progress/remainSec.
 *
 * Timeout arithmetic, for reading the expectations below: the executor emits
 * `Progress(durationSeconds + 30)` (a 30 s headroom, `SAFETY_PROGRESS_HEADROOM_SEC`), the frame handler
 * seeds `remainSec` with that full value, and `startTicker` then subtracts the headroom again and
 * unwinds the REAL duration. So a `Progress(90)` frame means a 60 s bar: tick 30 → 50 %, tick 60 → 100 %.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchSafetyCheckViewModelTest {

    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var activationExecutor: CarelevoActivationExecutor
    @Mock lateinit var patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase

    private lateinit var sut: CarelevoPatchSafetyCheckViewModel
    private lateinit var collectorScope: CoroutineScope

    /**
     * The one flow the SUT collects — stubbed once, before the VM is built, so tests never re-stub
     * `safetyProgress`. Tests drive the Progress branch by emitting into this instance.
     */
    private val progressFlow = MutableSharedFlow<SafetyProgress>(extraBufferCapacity = 16)

    private val address = "aa:bb:cc:dd:ee:ff"

    /** Virtual clock for the countdown ticker — see class KDoc. Fresh per test. */
    private lateinit var testScheduler: TestScheduler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        collectorScope = CoroutineScope(Dispatchers.Unconfined)
        // Virtualise the ticker's hard-wired computation scheduler. This also pins the tests that do
        // NOT drive time: with a TestScheduler that is never advanced, intervalRange cannot fire a
        // stray tick, so a seeded bar stays exactly as the Progress frame left it.
        testScheduler = TestScheduler()
        RxJavaPlugins.setComputationSchedulerHandler { testScheduler }
        // The force-discard Single ends in a 1-arg subscribe(onSuccess) with no onError, so a failing
        // Single routes an OnErrorNotImplementedException to the global Rx handler. Swallow it: the
        // doOnError side effects are what the test asserts.
        RxJavaPlugins.setErrorHandler { }

        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        // Default: a progress stream that never emits, so startSafetyCheck's collector parks and no
        // ticker is started — keeps the non-ticker tests fully deterministic. Overridden where the
        // Progress branch is under test.
        whenever(activationExecutor.safetyProgress).thenReturn(progressFlow)

        sut = CarelevoPatchSafetyCheckViewModel(
            aapsSchedulers = aapsSchedulers,
            aapsLogger = aapsLogger,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            activationExecutor = activationExecutor,
            patchForceDiscardUseCase = patchForceDiscardUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        collectorScope.cancel()
        RxJavaPlugins.reset()
        Dispatchers.resetMain()
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Build the result mock BEFORE it is handed to a `thenReturn` (avoids UnfinishedStubbingException). */
    private fun enactResult(success: Boolean): PumpEnactResult = mock<PumpEnactResult>().also {
        whenever(it.success).thenReturn(success)
    }

    /** `customCommand` is suspend — record the stub from inside a coroutine. */
    private fun stubCustomCommand(result: PumpEnactResult) = runBlocking {
        whenever(commandQueue.customCommand(any())).thenReturn(result)
    }

    /** Stub `customCommand` so it emits one 60 s Progress frame while "running", then returns [result]. */
    private fun stubCustomCommandEmittingProgress(
        result: PumpEnactResult,
        onSeeded: () -> Unit = {}
    ) = stubCustomCommandEmitting(listOf(SafetyProgress.Progress(60L)), result, onSeeded)

    /**
     * Stub `customCommand` so it emits [frames] one at a time while "running", then invokes [onSeeded]
     * and returns [result].
     *
     * The [yield]s are load-bearing, because `startSafetyCheck` launches its progress collector and
     * only THEN awaits `customCommand` — a stub that returns instantly is cancelled before the
     * collector ever runs. The leading yield lets the collector get scheduled and subscribe (without it
     * subscriptionCount stays 0 and the replay-0 SharedFlow drops the emit); the per-frame yield lets
     * the collector's dispatched resumption actually process that frame before the next one (or the
     * answer returning, and the terminal block cancelling it). Together they reproduce the real queue
     * round-trip's suspension.
     *
     * [onSeeded] runs at the one moment the ticker is both armed and not yet disposed, on the test
     * thread — so it is where the ticker tests advance [testScheduler] and sample the StateFlows.
     * Keep it assertion-free: it executes inside the VM's coroutine, so a thrown AssertionError would
     * surface as an unrelated coroutine failure rather than a clean test failure. Capture into a var
     * and assert after [CarelevoPatchSafetyCheckViewModel.startSafetyCheck] returns.
     */
    private fun stubCustomCommandEmitting(
        frames: List<SafetyProgress>,
        result: PumpEnactResult,
        onSeeded: () -> Unit = {}
    ) = runBlocking {
        whenever(commandQueue.customCommand(any())).doSuspendableAnswer {
            yield()
            frames.forEach {
                progressFlow.emit(it)
                yield()
            }
            onSeeded()
            result
        }
    }

    /** Collect one-shot events from the moment of the call, so BOTH emits of a two-event flow land. */
    private fun collectEvents(): MutableList<Event> {
        val events = mutableListOf<Event>()
        collectorScope.launch { sut.event.collect { events += it } }
        return events
    }

    private fun collectProgress(): MutableList<Int?> {
        val values = mutableListOf<Int?>()
        collectorScope.launch { sut.progress.collect { values += it } }
        return values
    }

    private fun collectRemainSec(): MutableList<Long?> {
        val values = mutableListOf<Long?>()
        collectorScope.launch { sut.remainSec.collect { values += it } }
        return values
    }

    private fun givenPatchState(state: PatchState?) {
        val optional: Optional<PatchState> = if (state == null) Optional.empty() else Optional.of(state)
        whenever(carelevoPatch.patchState).thenReturn(BehaviorSubject.createDefault(optional))
    }

    private fun givenPatchInfo(info: CarelevoPatchInfoDomainModel?) {
        val optional: Optional<CarelevoPatchInfoDomainModel> = if (info == null) Optional.empty() else Optional.of(info)
        whenever(carelevoPatch.patchInfo).thenReturn(BehaviorSubject.createDefault(optional))
    }

    private fun patchInfo(checkSafety: Boolean?) =
        CarelevoPatchInfoDomainModel(address = address, checkSafety = checkSafety)

    // ---- defaults / isCreated -----------------------------------------------------------------

    @Test
    fun `initial state is idle with no progress`() {
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(sut.progress.value).isNull()
        assertThat(sut.remainSec.value).isNull()
        assertThat(sut.isCreated).isFalse()
    }

    @Test
    fun `setIsCreated toggles the created latch`() {
        sut.setIsCreated(true)
        assertThat(sut.isCreated).isTrue()

        sut.setIsCreated(false)
        assertThat(sut.isCreated).isFalse()
    }

    // ---- triggerEvent / generateEventType -----------------------------------------------------

    @Test
    fun `triggerEvent forwards a known safety-check event unchanged`() {
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)
    }

    @Test
    fun `triggerEvent maps an unmapped safety-check event to NoAction`() {
        // NoAction is the only CarelevoConnectSafetyCheckEvent subtype generateEventType does not
        // enumerate, so it falls into the else branch (which itself returns NoAction).
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectSafetyCheckEvent.NoAction)

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.NoAction)
    }

    @Test
    fun `triggerEvent ignores events from another hierarchy`() {
        val events = collectEvents()

        sut.triggerEvent(CarelevoOverviewEvent.NoAction)

        assertThat(events).isEmpty()
    }

    // ---- startSafetyCheck ---------------------------------------------------------------------

    @Test
    fun `startSafetyCheck is refused when bluetooth is off`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
        val events = collectEvents()

        sut.startSafetyCheck()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
        assertThat(sut.progress.value).isNull()
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `startSafetyCheck completes with a full progress bar when the queue reports success`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))
        val events = collectEvents()

        sut.startSafetyCheck()

        assertThat(sut.progress.value).isEqualTo(100)
        assertThat(sut.remainSec.value).isEqualTo(0L)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
    }

    @Test
    fun `startSafetyCheck routes a CmdSafetyCheck through the command queue`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))

        sut.startSafetyCheck()

        verifyBlocking(commandQueue) { customCommand(any<CmdSafetyCheck>()) }
    }

    @Test
    fun `startSafetyCheck fails without completing the progress bar when the queue reports failure`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(false))
        val events = collectEvents()

        sut.startSafetyCheck()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
        // The failure branch must not fake a finished bar.
        assertThat(sut.progress.value).isNull()
        assertThat(sut.remainSec.value).isNull()
    }

    @Test
    fun `a safety-check progress frame seeds the countdown before the terminal success`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        val ok = enactResult(true)
        // progress/remainSec are CONFLATED StateFlows, so the seeded 0/60 is legitimately collapsed by
        // the terminal 100/0 that follows it — a collector can't reliably observe an intermediate that
        // is immediately overwritten. Sample the seed IN FLIGHT instead (inside the command answer,
        // after the frame has been processed but before the terminal block runs). That asserts the
        // ordering the UI depends on: the bar is seeded while the check runs, then completed.
        var seededProgress: Int? = null
        var seededRemain: Long? = null
        stubCustomCommandEmittingProgress(ok) {
            seededProgress = sut.progress.value
            seededRemain = sut.remainSec.value
        }
        val events = collectEvents()

        sut.startSafetyCheck()

        // Progress frame → bar reset to 0 and the countdown seeded with the frame's timeout...
        assertThat(seededProgress).isEqualTo(0)
        assertThat(seededRemain).isEqualTo(60L)
        // ...then the terminal success snaps it to a finished bar (the ticker is disposed first, so
        // no late tick can move these back).
        assertThat(sut.progress.value).isEqualTo(100)
        assertThat(sut.remainSec.value).isEqualTo(0L)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
    }

    @Test
    fun `a safety-check progress frame is followed by a failure event when the queue rejects`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        val failed = enactResult(false)
        stubCustomCommandEmittingProgress(failed)
        val progressValues = collectProgress()
        val events = collectEvents()

        sut.startSafetyCheck()

        // The frame really did seed the bar (0), and the rejection must not then complete it.
        assertThat(progressValues).contains(0)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
        assertThat(sut.progress.value).isNotEqualTo(100)
    }

    // ---- startSafetyCheck: the countdown ticker -------------------------------------------------

    @Test
    fun `the first tick re-bases the countdown from the padded timeout onto the real duration`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var seededRemain: Long? = null
        var tickZero: Pair<Int?, Long?>? = null
        // Progress(90) = a 60 s check + the executor's 30 s headroom.
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            seededRemain = sut.remainSec.value
            testScheduler.triggerActions()
            tickZero = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()

        // The frame handler seeds the FULL padded timeout...
        assertThat(seededRemain).isEqualTo(90L)
        // ...and the ticker immediately re-bases it onto the 60 s the check actually takes, so the
        // countdown the user reads is the duration, not the duration + slack.
        assertThat(tickZero).isEqualTo(0 to 60L)
    }

    @Test
    fun `the ticker advances the bar proportionally as the check runs`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var halfway: Pair<Int?, Long?>? = null
        var finished: Pair<Int?, Long?>? = null
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            testScheduler.triggerActions()
            testScheduler.advanceTimeBy(30, TimeUnit.SECONDS)
            halfway = sut.progress.value to sut.remainSec.value
            testScheduler.advanceTimeBy(30, TimeUnit.SECONDS)
            finished = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()

        // 30 s into a 60 s window.
        assertThat(halfway).isEqualTo(50 to 30L)
        // The ticker reaches a full bar on its own, before the queue result arrives.
        assertThat(finished).isEqualTo(100 to 0L)
    }

    @Test
    fun `the ticker stops at a full bar and never overshoots its window`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var overrun: Pair<Int?, Long?>? = null
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            // Run far past the 60 s window: the emission count bounds the stream, and the percent /
            // remain guards clamp it, so a slow check cannot drive the bar past 100 % or the
            // countdown below zero.
            testScheduler.advanceTimeBy(5, TimeUnit.MINUTES)
            overrun = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()

        assertThat(overrun).isEqualTo(100 to 0L)
    }

    @Test
    fun `the progress bar never moves backwards while ticking`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Sampled synchronously inside the ticker window rather than via collectProgress(): progress is
        // a CONFLATED StateFlow, and every write here happens nested inside the VM's coroutine, so an
        // Unconfined collector's resumptions only drain once the outermost coroutine unwinds — by then
        // the value is already the terminal 100 and the whole history has been conflated away. Sampling
        // the value at each advance is the only way to observe the bar actually moving.
        val bar = mutableListOf<Int>()
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            testScheduler.triggerActions()
            sut.progress.value?.let(bar::add)                       // seeded 0
            repeat(6) {
                testScheduler.advanceTimeBy(10, TimeUnit.SECONDS)   // 60 s bar, sampled every 10 s
                sut.progress.value?.let(bar::add)
            }
        }

        sut.startSafetyCheck()

        sut.progress.value?.let(bar::add)                           // terminal
        // Every value the UI saw, from the seeded 0 through the ticks to the terminal 100, is
        // monotonic — a progress bar that jumps backwards reads as a stalled/restarted check.
        assertThat(bar).isInOrder()
        assertThat(bar.first()).isEqualTo(0)
        assertThat(bar.last()).isEqualTo(100)
        // ...and it genuinely moved through the middle rather than snapping 0 → 100.
        assertThat(bar.any { it in 1..99 }).isTrue()
    }

    @Test
    fun `a second progress frame re-arms the countdown and abandons the first ticker`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        val ok = enactResult(true)
        var afterFirstWindow: Pair<Int?, Long?>? = null
        var afterReArm: Pair<Int?, Long?>? = null
        runBlocking {
            whenever(commandQueue.customCommand(any())).doSuspendableAnswer {
                yield()
                progressFlow.emit(SafetyProgress.Progress(90L)) // 60 s window
                yield()
                testScheduler.triggerActions()
                testScheduler.advanceTimeBy(30, TimeUnit.SECONDS)
                afterFirstWindow = sut.progress.value to sut.remainSec.value
                progressFlow.emit(SafetyProgress.Progress(60L)) // re-armed onto a 30 s window
                yield()
                testScheduler.triggerActions()
                testScheduler.advanceTimeBy(5, TimeUnit.SECONDS)
                afterReArm = sut.progress.value to sut.remainSec.value
                ok
            }
        }

        sut.startSafetyCheck()

        assertThat(afterFirstWindow).isEqualTo(50 to 30L)
        // 5 s into the new 30 s window: the bar was reset and is climbing again on the new base.
        // This is what proves the first ticker was disposed rather than left running: a live one
        // would be 35 s into its own window by now and, through the monotonic max, would have
        // dragged the bar up to 58 % instead of 16 %.
        assertThat(afterReArm).isEqualTo(16 to 25L)
    }

    @Test
    fun `terminal safety-check frames do not arm the countdown`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var duringCheck: Pair<Int?, Long?>? = null
        val terminalFrames = listOf(
            SafetyProgress.Success(SafetyCheckResultModel(SafetyCheckResult.SUCCESS, 30, 60)),
            SafetyProgress.Error(IllegalStateException("safety check failed"))
        )
        stubCustomCommandEmitting(terminalFrames, enactResult(true)) {
            testScheduler.advanceTimeBy(60, TimeUnit.SECONDS)
            duringCheck = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()

        // Only Progress frames drive the bar; Success/Error are the executor's own bookkeeping and
        // must leave the countdown untouched (no ticker was ever started, hence advancing does
        // nothing).
        assertThat(duringCheck).isEqualTo(null to null)
        // The queue result, not the frames, is what completes the check.
        assertThat(sut.progress.value).isEqualTo(100)
    }

    @Test
    fun `the terminal success snaps a half-run bar to complete and stops the ticker`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            testScheduler.triggerActions()
            testScheduler.advanceTimeBy(30, TimeUnit.SECONDS) // bar sits at 50 %
        }

        sut.startSafetyCheck()

        // A check that answers early jumps the bar straight to done rather than letting it crawl.
        assertThat(sut.progress.value).isEqualTo(100)
        assertThat(sut.remainSec.value).isEqualTo(0L)

        // The ticker was disposed before those writes, so leftover virtual time cannot resurrect the
        // countdown. (A live ticker would put remainSec back to 20 here — the bar itself is masked by
        // the monotonic max, so remainSec is what actually catches the leak.)
        testScheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        assertThat(sut.progress.value).isEqualTo(100)
        assertThat(sut.remainSec.value).isEqualTo(0L)
    }

    @Test
    fun `a rejected check leaves the bar where the ticker stopped instead of completing it`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(false)) {
            testScheduler.triggerActions()
            testScheduler.advanceTimeBy(30, TimeUnit.SECONDS)
        }
        val events = collectEvents()

        sut.startSafetyCheck()

        // The failure path must not fake a finished bar — it freezes at the last tick.
        assertThat(sut.progress.value).isEqualTo(50)
        assertThat(sut.remainSec.value).isEqualTo(30L)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
    }

    @Test
    fun `onSafetyCheckComplete finishes the bar and signals completion`() {
        val events = collectEvents()

        sut.onSafetyCheckComplete()

        assertThat(sut.progress.value).isEqualTo(100)
        assertThat(sut.remainSec.value).isEqualTo(0L)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
    }

    // ---- startDiscardProcess: gating ----------------------------------------------------------

    @Test
    fun `startDiscardProcess short-circuits when the patch was never booted`() {
        givenPatchState(PatchState.NotConnectedNotBooting)
        val events = collectEvents()

        sut.startDiscardProcess()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardComplete)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `startDiscardProcess short-circuits when there is no patch state at all`() {
        givenPatchState(null)
        val events = collectEvents()

        sut.startDiscardProcess()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardComplete)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `startDiscardProcess completes through the queue when the patch is reachable`() {
        givenPatchState(PatchState.ConnectedBooted)
        stubCustomCommand(enactResult(true))
        val events = collectEvents()

        sut.startDiscardProcess()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardComplete)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
        // The BLE teardown lives inside CmdDiscard on the queue thread, not in the VM.
        verify(carelevoPatch, never()).discardTeardown()
    }

    @Test
    fun `startDiscardProcess runs on the connected-not-booted branch too`() {
        givenPatchState(PatchState.ConnectedNoBooted)
        stubCustomCommand(enactResult(true))
        val events = collectEvents()

        sut.startDiscardProcess()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardComplete)
        verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
    }

    // ---- startDiscardProcess → force-discard fallback ------------------------------------------

    @Test
    fun `a rejected queue discard falls back to force-discard and tears the patch down`() {
        givenPatchState(PatchState.NotConnectedBooted)
        stubCustomCommand(enactResult(false))
        val success: ResponseResult<CarelevoUseCaseResponse> = ResponseResult.Success(ResultSuccess)
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(success))
        val events = collectEvents()

        sut.startDiscardProcess()

        verify(carelevoPatch).discardTeardown()
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardComplete)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `a force-discard that errors leaves the patch intact and reports failure`() {
        givenPatchState(PatchState.ConnectedBooted)
        stubCustomCommand(enactResult(false))
        val error: ResponseResult<CarelevoUseCaseResponse> = ResponseResult.Error(IllegalStateException("delete patch info is failed"))
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(error))
        val events = collectEvents()

        sut.startDiscardProcess()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verify(carelevoPatch, never()).discardTeardown()
    }

    @Test
    fun `a force-discard that returns Failure reports failure`() {
        givenPatchState(PatchState.ConnectedBooted)
        stubCustomCommand(enactResult(false))
        val failure: ResponseResult<CarelevoUseCaseResponse> = ResponseResult.Failure("nope")
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(failure))
        val events = collectEvents()

        sut.startDiscardProcess()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verify(carelevoPatch, never()).discardTeardown()
    }

    @Test
    fun `a force-discard whose stream throws reports failure through doOnError`() {
        givenPatchState(PatchState.ConnectedBooted)
        stubCustomCommand(enactResult(false))
        whenever(patchForceDiscardUseCase.execute())
            .thenReturn(Single.error(RuntimeException("db down")))
        val events = collectEvents()

        sut.startDiscardProcess()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verify(carelevoPatch, never()).discardTeardown()
    }

    // ---- retryAdditionalPriming ---------------------------------------------------------------

    @Test
    fun `retryAdditionalPriming is refused when bluetooth is off`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
        val events = collectEvents()

        sut.retryAdditionalPriming()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `retryAdditionalPriming returns to idle without feedback on success`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))
        val events = collectEvents()

        sut.retryAdditionalPriming()

        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.DiscardFailed)
        verifyBlocking(commandQueue) { customCommand(any<CmdAdditionalPriming>()) }
    }

    @Test
    fun `retryAdditionalPriming surfaces feedback and returns to idle on failure`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(false))
        val events = collectEvents()

        sut.retryAdditionalPriming()

        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.DiscardFailed)
    }

    // ---- isSafetyCheckPassed ------------------------------------------------------------------

    @Test
    fun `isSafetyCheckPassed is true only when the patch recorded a passed check`() {
        givenPatchInfo(patchInfo(checkSafety = true))

        assertThat(sut.isSafetyCheckPassed()).isTrue()
    }

    @Test
    fun `isSafetyCheckPassed is false when the patch recorded a failed check`() {
        givenPatchInfo(patchInfo(checkSafety = false))

        assertThat(sut.isSafetyCheckPassed()).isFalse()
    }

    @Test
    fun `isSafetyCheckPassed is false when the check was never recorded`() {
        givenPatchInfo(patchInfo(checkSafety = null))

        assertThat(sut.isSafetyCheckPassed()).isFalse()
    }

    @Test
    fun `isSafetyCheckPassed is false when there is no patch info`() {
        givenPatchInfo(null)

        assertThat(sut.isSafetyCheckPassed()).isFalse()
    }

    // ---- isConnected --------------------------------------------------------------------------

    @Test
    fun `isConnected is false without a paired patch address`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)

        assertThat(sut.isConnected()).isFalse()
    }

    @Test
    fun `isConnected is false when bluetooth is off`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(address)
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        assertThat(sut.isConnected()).isFalse()
    }

    @Test
    fun `isConnected is true when a session can be attempted`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(address)
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)

        assertThat(sut.isConnected()).isTrue()
    }

    // ---- onCleared ----------------------------------------------------------------------------

    @Test
    fun `onCleared disposes the force-discard subscription without throwing`() {
        // onCleared is protected on ViewModel and the VM is final, so drive it reflectively.
        val onCleared = sut.javaClass.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true

        onCleared.invoke(sut)

        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }
}
