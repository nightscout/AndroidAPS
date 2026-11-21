package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.carelevo.command.CarelevoActivationExecutor
import app.aaps.pump.carelevo.command.CmdAdditionalPriming
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.command.CmdSafetyCheck
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.latestValueFlow
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
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectSafetyCheckEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoOverviewEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
 * The force discard runs in `viewModelScope` too, so under Unconfined Main it also ends inside the call.
 *
 * The countdown ticker is a `viewModelScope` coroutine that waits one second between ticks with
 * `delay`. Under Unconfined Main that `delay` would use the real clock, so the ticker tests install a
 * [StandardTestDispatcher] as Main instead and move its virtual clock (`runCurrent` / `advanceTimeBy`
 * on the test scheduler). A tick is fully applied to the StateFlows by the time that call returns. In
 * the other tests the ticker never ticks: under Unconfined Main it is queued behind the command answer
 * that is still running, and the end of that answer cancels it, so a seeded bar stays exactly as the
 * Progress frame left it.
 *
 * That is also why the ticker tests move the clock from INSIDE the `customCommand` answer (see
 * [stubCustomCommandEmitting]): that is the only window in which the ticker is alive, since the
 * terminal block cancels it before writing the final progress/remainSec.
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

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var activationExecutor: CarelevoActivationExecutor
    @Mock lateinit var patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase
    @Mock lateinit var carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase

    private lateinit var sut: CarelevoPatchSafetyCheckViewModel
    private lateinit var collectorScope: CoroutineScope

    /**
     * The one flow the SUT collects — stubbed once, before the VM is built, so tests never re-stub
     * `safetyProgress`. Tests drive the Progress branch by emitting into this instance.
     */
    private val progressFlow = MutableSharedFlow<SafetyProgress>(extraBufferCapacity = 16)

    private val address = "aa:bb:cc:dd:ee:ff"

    @BeforeEach
    fun setUp() {
        // Under Unconfined Main the ticker never gets to tick (see class KDoc), so a seeded bar stays
        // exactly as the Progress frame left it. The ticker tests install a virtual clock instead.
        Dispatchers.setMain(Dispatchers.Unconfined)
        collectorScope = CoroutineScope(Dispatchers.Unconfined)

        // Default: a progress stream that never emits, so startSafetyCheck's collector parks and no
        // ticker is started — keeps the non-ticker tests fully deterministic. Overridden where the
        // Progress branch is under test.
        whenever(activationExecutor.safetyProgress).thenReturn(progressFlow)

        sut = CarelevoPatchSafetyCheckViewModel(
            aapsLogger = aapsLogger,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            activationExecutor = activationExecutor,
            patchForceDiscardUseCase = patchForceDiscardUseCase,
            carelevoAlarmInfoUseCase = carelevoAlarmInfoUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        collectorScope.cancel()
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

    /**
     * Answer the priming pulses in turn: true = delivered, false = the patch answered "not success", null = no answer.
     * After the last one, the last answer repeats.
     */
    private fun stubPrimingAnswers(vararg answers: Boolean?) = runBlocking {
        val delivered = enactResult(true)
        val failed = enactResult(false)
        var call = 0
        whenever(commandQueue.customCommand(any())).thenAnswer { invocation ->
            val answer = answers.getOrElse(call++) { answers.last() }
            if (answer == false) (invocation.arguments[0] as CmdAdditionalPriming).patchRefused = true
            if (answer == true) delivered else failed
        }
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
     * [onSeeded] runs at the one moment the ticker is both armed and not yet cancelled, on the test
     * thread — so it is where the ticker tests move the virtual clock and sample the StateFlows.
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
        whenever(carelevoPatch.patchState).thenReturn(latestValueFlow<PatchState?>().apply { tryEmit(state) })
    }

    private fun givenPatchInfo(info: CarelevoPatchInfoDomainModel?) {
        whenever(carelevoPatch.patchInfo).thenReturn(latestValueFlow<CarelevoPatchInfoDomainModel?>().apply { tryEmit(info) })
    }

    /**
     * Clear the VM the way the framework does when its screen goes away: `viewModelScope` is cancelled,
     * then `onCleared` runs.
     */
    private fun clearViewModel() {
        val store = ViewModelStore()
        ViewModelProvider(store, viewModelFactory { initializer { sut } })[CarelevoPatchSafetyCheckViewModel::class]
        store.clear()
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

    // A check that never started is dialled again, so these tests need a virtual clock for the wait.
    @Test
    fun `startSafetyCheck retries once when the check never started, then fails`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(false))
        val events = collectEvents()

        sut.startSafetyCheck()
        advanceUntilIdle()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
        verifyBlocking(commandQueue, times(2)) { customCommand(any<CmdSafetyCheck>()) }
        // The failure branch must not fake a finished bar.
        assertThat(sut.progress.value).isNull()
        assertThat(sut.remainSec.value).isNull()
    }

    @Test
    fun `startSafetyCheck clears the finished bar before the next check runs`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))

        sut.startSafetyCheck()
        advanceUntilIdle()
        assertThat(sut.progress.value).isEqualTo(100)

        sut.startSafetyCheck()

        // Cleared synchronously, so the next patch never shows the previous one's full bar.
        assertThat(sut.progress.value).isNull()
        assertThat(sut.remainSec.value).isNull()
    }

    // The progress StateFlow keeps the finished 100 after a success, and the ViewModel outlives the
    // wizard — so the next patch must not read that as "this check started".
    @Test
    fun `startSafetyCheck still retries after an earlier check finished successfully`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))

        sut.startSafetyCheck()
        advanceUntilIdle()
        assertThat(sut.progress.value).isEqualTo(100)

        stubCustomCommand(enactResult(false))
        sut.startSafetyCheck()
        advanceUntilIdle()

        // One attempt for the success, then two for the check that never started.
        verifyBlocking(commandQueue, times(3)) { customCommand(any<CmdSafetyCheck>()) }
    }

    @Test
    fun `startSafetyCheck does not retry a check that had already started`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommandEmittingProgress(enactResult(false))
        val events = collectEvents()

        sut.startSafetyCheck()
        advanceUntilIdle()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
        // A progress frame arrived, so the patch really did fail the check — one attempt only.
        verifyBlocking(commandQueue, times(1)) { customCommand(any<CmdSafetyCheck>()) }
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
        // ...then the terminal success snaps it to a finished bar (the ticker is cancelled first, so
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

    // The ticker waits with `delay`, so these tests swap Main for a virtual clock (see class KDoc).
    // A coroutine `advanceTimeBy(d)` runs what is due BEFORE the target time, so `runCurrent()` after
    // it runs the tick that falls exactly on the target.
    @Test
    fun `the first tick re-bases the countdown from the padded timeout onto the real duration`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var seededRemain: Long? = null
        var tickZero: Pair<Int?, Long?>? = null
        // Progress(90) = a 60 s check + the executor's 30 s headroom.
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            seededRemain = sut.remainSec.value
            testScheduler.runCurrent()
            tickZero = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

        // The frame handler seeds the FULL padded timeout...
        assertThat(seededRemain).isEqualTo(90L)
        // ...and the ticker immediately re-bases it onto the 60 s the check actually takes, so the
        // countdown the user reads is the duration, not the duration + slack.
        assertThat(tickZero).isEqualTo(0 to 60L)
    }

    @Test
    fun `the ticker advances the bar proportionally as the check runs`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var halfway: Pair<Int?, Long?>? = null
        var finished: Pair<Int?, Long?>? = null
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(30.seconds)
            testScheduler.runCurrent()
            halfway = sut.progress.value to sut.remainSec.value
            testScheduler.advanceTimeBy(30.seconds)
            testScheduler.runCurrent()
            finished = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

        // 30 s into a 60 s window.
        assertThat(halfway).isEqualTo(50 to 30L)
        // The ticker reaches a full bar on its own, before the queue result arrives.
        assertThat(finished).isEqualTo(100 to 0L)
    }

    @Test
    fun `a frame with no real duration counts down the whole time limit`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var tickZero: Pair<Int?, Long?>? = null
        var halfway: Pair<Int?, Long?>? = null
        var finished: Pair<Int?, Long?>? = null
        // Progress(30) = a duration of 0 from the patch + the 30 s headroom. Before, the countdown stood still at 30.
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(30L)), enactResult(true)) {
            testScheduler.runCurrent()
            tickZero = sut.progress.value to sut.remainSec.value
            testScheduler.advanceTimeBy(15.seconds)
            testScheduler.runCurrent()
            halfway = sut.progress.value to sut.remainSec.value
            testScheduler.advanceTimeBy(15.seconds)
            testScheduler.runCurrent()
            finished = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

        assertThat(tickZero).isEqualTo(0 to 30L)
        assertThat(halfway).isEqualTo(50 to 15L)
        assertThat(finished).isEqualTo(100 to 0L)
    }

    @Test
    fun `the ticker stops at a full bar and never overshoots its window`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var overrun: Pair<Int?, Long?>? = null
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            // Run far past the 60 s window: the tick count bounds the loop, and the percent /
            // remain guards clamp it, so a slow check cannot drive the bar past 100 % or the
            // countdown below zero.
            testScheduler.advanceTimeBy(5.minutes)
            testScheduler.runCurrent()
            overrun = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

        assertThat(overrun).isEqualTo(100 to 0L)
    }

    @Test
    fun `the progress bar never moves backwards while ticking`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Sampled inside the ticker window rather than via collectProgress(): progress is a CONFLATED
        // StateFlow, so a collector that runs later sees only the last of several writes, and the
        // history can be conflated away. Sampling the value at each advance is the only way to
        // observe the bar actually moving.
        val bar = mutableListOf<Int>()
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            testScheduler.runCurrent()
            sut.progress.value?.let(bar::add)                       // seeded 0
            repeat(6) {
                testScheduler.advanceTimeBy(10.seconds)             // 60 s bar, sampled every 10 s
                testScheduler.runCurrent()
                sut.progress.value?.let(bar::add)
            }
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

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
    fun `a second progress frame re-arms the countdown and abandons the first ticker`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        val ok = enactResult(true)
        var afterFirstWindow: Pair<Int?, Long?>? = null
        var afterReArm: Pair<Int?, Long?>? = null
        runBlocking {
            whenever(commandQueue.customCommand(any())).doSuspendableAnswer {
                yield()
                progressFlow.emit(SafetyProgress.Progress(90L)) // 60 s window
                yield()
                testScheduler.runCurrent()
                testScheduler.advanceTimeBy(30.seconds)
                testScheduler.runCurrent()
                afterFirstWindow = sut.progress.value to sut.remainSec.value
                progressFlow.emit(SafetyProgress.Progress(60L)) // re-armed onto a 30 s window
                yield()
                testScheduler.runCurrent()
                testScheduler.advanceTimeBy(5.seconds)
                testScheduler.runCurrent()
                afterReArm = sut.progress.value to sut.remainSec.value
                ok
            }
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

        assertThat(afterFirstWindow).isEqualTo(50 to 30L)
        // 5 s into the new 30 s window: the bar was reset and is climbing again on the new base.
        // This is what proves the first ticker was cancelled rather than left running: a live one
        // would be 35 s into its own window by now and, through the monotonic max, would have
        // dragged the bar up to 58 % instead of 16 %.
        assertThat(afterReArm).isEqualTo(16 to 25L)
    }

    @Test
    fun `terminal safety-check frames do not arm the countdown`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        var duringCheck: Pair<Int?, Long?>? = null
        val terminalFrames = listOf(
            SafetyProgress.Success(SafetyCheckResultModel(SafetyCheckResult.SUCCESS, 30, 60)),
            SafetyProgress.Error(IllegalStateException("safety check failed"))
        )
        stubCustomCommandEmitting(terminalFrames, enactResult(true)) {
            testScheduler.advanceTimeBy(60.seconds)
            testScheduler.runCurrent()
            duringCheck = sut.progress.value to sut.remainSec.value
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

        // Only Progress frames drive the bar; Success/Error are the executor's own bookkeeping and
        // must leave the countdown untouched (no ticker was ever started, hence advancing does
        // nothing).
        assertThat(duringCheck).isEqualTo(null to null)
        // The queue result, not the frames, is what completes the check.
        assertThat(sut.progress.value).isEqualTo(100)
    }

    @Test
    fun `the terminal success snaps a half-run bar to complete and stops the ticker`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(true)) {
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(30.seconds) // bar sits at 50 %
            testScheduler.runCurrent()
        }

        sut.startSafetyCheck()
        advanceUntilIdle()

        // A check that answers early jumps the bar straight to done rather than letting it crawl.
        assertThat(sut.progress.value).isEqualTo(100)
        assertThat(sut.remainSec.value).isEqualTo(0L)

        // The ticker was cancelled before those writes, so leftover virtual time cannot resurrect the
        // countdown. (A live ticker would put remainSec back to 20 here — the bar itself is masked by
        // the monotonic max, so remainSec is what actually catches the leak.)
        testScheduler.advanceTimeBy(10.seconds)
        testScheduler.runCurrent()
        assertThat(sut.progress.value).isEqualTo(100)
        assertThat(sut.remainSec.value).isEqualTo(0L)
    }

    @Test
    fun `a rejected check leaves the bar where the ticker stopped instead of completing it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommandEmitting(listOf(SafetyProgress.Progress(90L)), enactResult(false)) {
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(30.seconds)
            testScheduler.runCurrent()
        }
        val events = collectEvents()

        sut.startSafetyCheck()
        advanceUntilIdle()

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
        whenever { patchForceDiscardUseCase.execute() }.thenReturn(success)
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
        whenever { patchForceDiscardUseCase.execute() }.thenReturn(error)
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
        whenever { patchForceDiscardUseCase.execute() }.thenReturn(failure)
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
        whenever { patchForceDiscardUseCase.execute() }
            .thenAnswer { throw RuntimeException("db down") }
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

    // The burst sleeps between pulses, so these tests swap Main for a virtual clock. setUp
    // reinstalls Unconfined (see class KDoc) before every test, so the swap cannot leak.
    @Test
    fun `retryAdditionalPriming fires a burst of three pulses and returns to idle`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))

        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        verifyBlocking(commandQueue, times(3)) { customCommand(any<CmdAdditionalPriming>()) }
    }

    /** A real patch answers each pulse after about 30 s, so the screen says which pulse is running. */
    @Test
    fun `retryAdditionalPriming says which pulse it is sending and clears it at the end`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        val seen = mutableListOf<Int?>()
        whenever(commandQueue.customCommand(any())).doSuspendableAnswer {
            seen += sut.primingPulse.value
            enactResult(true)
        }

        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(seen).containsExactly(1, 2, 3).inOrder()
        assertThat(sut.primingPulse.value).isNull()
        assertThat(sut.primingPulseCount).isEqualTo(3)
    }

    @Test
    fun `retryAdditionalPriming clears the pulse when the link fails`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(false))

        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(sut.primingPulse.value).isNull()
    }

    @Test
    fun `retryAdditionalPriming stops the burst and reports a link failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(false))
        val events = collectEvents()

        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.AdditionalPrimingFailed)
        // The first pulse is attempted twice, then the burst gives up without sending the other two.
        verifyBlocking(commandQueue, times(2)) { customCommand(any<CmdAdditionalPriming>()) }
    }

    @Test
    fun `retryAdditionalPriming keeps its allowance when the link failed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(false))

        sut.retryAdditionalPriming()
        advanceUntilIdle()
        stubCustomCommand(enactResult(true))
        sut.retryAdditionalPriming()
        advanceUntilIdle()

        // Two failed attempts on the abandoned burst, then a full burst of three.
        verifyBlocking(commandQueue, times(5)) { customCommand(any<CmdAdditionalPriming>()) }
        verifyBlocking(carelevoAlarmInfoUseCase, never()) { upsertAlarm(any()) }
    }

    @Test
    fun `a pulse the patch refuses twice ends in the self-diagnosis alarm`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubPrimingAnswers(false, false)
        val events = collectEvents()

        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.PrimingRetryLimitExceeded)
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.AdditionalPrimingFailed)
        verifyBlocking(commandQueue, times(2)) { customCommand(any<CmdAdditionalPriming>()) }
        verifyBlocking(carelevoAlarmInfoUseCase) { upsertAlarm(any()) }
    }

    @Test
    fun `a pulse the patch refuses once and then takes goes on with the burst`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubPrimingAnswers(false, true)
        val events = collectEvents()

        sut.retryAdditionalPriming()
        advanceUntilIdle()

        // Pulse 1 twice, then pulses 2 and 3.
        verifyBlocking(commandQueue, times(4)) { customCommand(any<CmdAdditionalPriming>()) }
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.PrimingRetryLimitExceeded)
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.AdditionalPrimingFailed)
        verifyBlocking(carelevoAlarmInfoUseCase, never()) { upsertAlarm(any()) }
    }

    @Test
    fun `a refusal and then no answer is the link, not a bad patch`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubPrimingAnswers(false, null)
        val events = collectEvents()

        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.AdditionalPrimingFailed)
        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.PrimingRetryLimitExceeded)
        verifyBlocking(carelevoAlarmInfoUseCase, never()) { upsertAlarm(any()) }
    }

    // The ViewModel outlives the wizard, so a spent allowance must not follow the next patch in.
    @Test
    fun `startSafetyCheck restores the priming allowance`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))
        val events = collectEvents()

        sut.retryAdditionalPriming()
        advanceUntilIdle()
        sut.startSafetyCheck()
        advanceUntilIdle()
        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(events).doesNotContain(CarelevoConnectSafetyCheckEvent.PrimingRetryLimitExceeded)
        verifyBlocking(carelevoAlarmInfoUseCase, never()) { upsertAlarm(any()) }
    }

    @Test
    fun `retryAdditionalPriming raises the self-diagnosis alarm once the allowance is spent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(enactResult(true))
        val events = collectEvents()

        sut.retryAdditionalPriming()
        advanceUntilIdle()
        sut.retryAdditionalPriming()
        advanceUntilIdle()

        assertThat(events).contains(CarelevoConnectSafetyCheckEvent.PrimingRetryLimitExceeded)
        // Only the first request reaches the patch; the second is refused before any pulse.
        verifyBlocking(commandQueue, times(3)) { customCommand(any<CmdAdditionalPriming>()) }
        verifyBlocking(carelevoAlarmInfoUseCase) { upsertAlarm(any()) }
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
        // The VM no longer overrides onCleared: its work lives in viewModelScope, which the framework
        // cancels when it clears the VM. So clear it the framework way.
        clearViewModel()

        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }
}
