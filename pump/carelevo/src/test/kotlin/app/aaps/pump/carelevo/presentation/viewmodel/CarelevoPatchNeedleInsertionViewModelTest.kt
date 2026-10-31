package app.aaps.pump.carelevo.presentation.viewmodel

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.command.CmdNeedleCheck
import app.aaps.pump.carelevo.command.CmdSetBasal
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectNeedleEvent
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Optional

/**
 * Unit tests for [CarelevoPatchNeedleInsertionViewModel] — the activation wizard's needle-insertion
 * step.
 *
 * **Why Robolectric.** Every collaborator is mockable, but the therapy-event bookkeeping
 * (`insertTherapyEventWithSingleRetry` / `insertCannulaChangeWithSite`) calls
 * `android.os.SystemClock.sleep(...)` on its recovery path. Under a plain JVM unit test the
 * mockable `android.jar` throws "Method sleep in android.os.SystemClock not mocked" (this project
 * does not set `returnDefaultValues`), so the insert-retry branches would be untestable. Under
 * [RobolectricTestRunner] `SystemClock.sleep` is shadowed and returns immediately, letting
 * `startSetBasal success retries...` exercise the recovery inserts for real. Everything else
 * behaves identically to a JVM test — no real Context is needed by this ViewModel.
 *
 * **Coroutines.** `viewModelScope` is backed by `Dispatchers.Main`, so a [StandardTestDispatcher] is
 * installed as Main and its scheduler is handed to `runTest`, giving Main and the test body one
 * shared virtual clock: `advanceUntilIdle()` then flushes the VM's `launch`ed work (including the
 * `delay(1000)` after `connectNewPump`) before state is asserted.
 *
 * **Rx.** [AapsSchedulers] is stubbed to [Schedulers.trampoline] so `observePatchInfo` and the
 * force-discard chain deliver synchronously on the test thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class CarelevoPatchNeedleInsertionViewModelTest {

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var pumpSync: PumpSync
    private lateinit var persistenceLayer: PersistenceLayer
    private lateinit var aapsSchedulers: AapsSchedulers
    private lateinit var carelevoPatch: CarelevoPatch
    private lateinit var commandQueue: CommandQueue
    private lateinit var patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase
    private lateinit var carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase

    // The BehaviorSubjects CarelevoPatch exposes; `.value` is what the VM reads for serial /
    // fail-count / patch-state, and onNext() drives observePatchInfo's subscription.
    private lateinit var patchInfoSubject: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>>
    private lateinit var patchStateSubject: BehaviorSubject<Optional<PatchState>>
    private lateinit var profileSubject: BehaviorSubject<Optional<Profile>>

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var sut: CarelevoPatchNeedleInsertionViewModel

    @Before
    fun setUp() {
        aapsLogger = mock()
        pumpSync = mock()
        persistenceLayer = mock()
        aapsSchedulers = mock()
        carelevoPatch = mock()
        commandQueue = mock()
        patchForceDiscardUseCase = mock()
        carelevoAlarmInfoUseCase = mock()

        patchInfoSubject = BehaviorSubject.create()
        patchStateSubject = BehaviorSubject.create()
        profileSubject = BehaviorSubject.create()

        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(carelevoPatch.patchInfo).thenReturn(patchInfoSubject)
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject)
        whenever(carelevoPatch.profile).thenReturn(profileSubject)
        // Wizard default: site rotation step skipped -> no location/arrow to patch onto the event.
        whenever(carelevoPatch.sitePlacementLocation).thenReturn(TE.Location.NONE)
        whenever(carelevoPatch.sitePlacementArrow).thenReturn(TE.Arrow.NONE)

        // The force-discard chain ends in a single-arg subscribe(onSuccess); an errored source there
        // routes an OnErrorNotImplementedException to the global handler. Swallow it so the
        // doOnError branch can be tested without polluting the run.
        RxJavaPlugins.setErrorHandler { }

        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        sut = CarelevoPatchNeedleInsertionViewModel(
            aapsLogger = aapsLogger,
            pumpSync = pumpSync,
            persistenceLayer = persistenceLayer,
            aapsSchedulers = aapsSchedulers,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            patchForceDiscardUseCase = patchForceDiscardUseCase,
            carelevoAlarmInfoUseCase = carelevoAlarmInfoUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        RxJavaPlugins.setErrorHandler(null)
    }

    // ---- helpers ------------------------------------------------------------------------------

    /**
     * Build the stubbed result mock BEFORE it is handed to thenReturn — stubbing another mock inside
     * a thenReturn argument trips Mockito's UnfinishedStubbingException.
     */
    private fun enactResult(success: Boolean): PumpEnactResult = mock<PumpEnactResult>().also {
        whenever(it.success).thenReturn(success)
    }

    private fun patchInfo(
        checkNeedle: Boolean? = null,
        needleFailedCount: Int? = null,
        manufactureNumber: String? = "SN-1"
    ) = CarelevoPatchInfoDomainModel(
        address = "AA:BB:CC:DD:EE:FF",
        manufactureNumber = manufactureNumber,
        checkNeedle = checkNeedle,
        needleFailedCount = needleFailedCount
    )

    private fun therapyEvent(type: TE.Type = TE.Type.CANNULA_CHANGE) =
        TE(timestamp = 1_000L, type = type, glucoseUnit = GlucoseUnit.MGDL)

    /**
     * Subscribe to the VM's one-shot event flow for the whole test. The collector runs on an
     * unconfined test dispatcher so it is subscribed before any action is triggered (the flow is
     * consume-once, so exactly one collector must own the emissions).
     */
    private fun TestScope.collectEvents(): MutableList<Event> {
        val events = mutableListOf<Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sut.event.collect { events += it }
        }
        return events
    }

    /** Bluetooth on + a profile set: the happy-path preconditions for startSetBasal. */
    private fun givenReadyToSetBasal() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        profileSubject.onNext(Optional.of(mock<Profile>()))
    }

    // ---- simple state / accessors -------------------------------------------------------------

    @Test
    fun `isCreated defaults to false and follows setIsCreated`() {
        assertThat(sut.isCreated).isFalse()

        sut.setIsCreated(true)
        assertThat(sut.isCreated).isTrue()

        sut.setIsCreated(false)
        assertThat(sut.isCreated).isFalse()
    }

    @Test
    fun `initial state is Idle with the needle not inserted`() {
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(sut.isNeedleInsert.value).isFalse()
    }

    @Test
    fun `needleFailCount is null when there is no patch info`() {
        assertThat(sut.needleFailCount()).isNull()
    }

    @Test
    fun `needleFailCount reads the count off the patch info`() {
        patchInfoSubject.onNext(Optional.of(patchInfo(needleFailedCount = 2)))

        assertThat(sut.needleFailCount()).isEqualTo(2)
    }

    // ---- triggerEvent -------------------------------------------------------------------------

    @Test
    fun `triggerEvent forwards a needle event to the event flow`() = runTest(testDispatcher.scheduler) {
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleComplete(true))
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectNeedleEvent.CheckNeedleComplete(true))
    }

    @Test
    fun `triggerEvent maps an unmapped needle event to NoAction`() = runTest(testDispatcher.scheduler) {
        // NoAction is not listed in generateEventType's when -> falls through to the else branch.
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectNeedleEvent.NoAction)
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectNeedleEvent.NoAction)
    }

    @Test
    fun `triggerEvent forwards the not-connected message to the event flow`() = runTest(testDispatcher.scheduler) {
        // ShowMessageCarelevoIsNotConnected has its own generateEventType branch but no VM caller;
        // it is only reachable through triggerEvent from the step composable.
        val events = collectEvents()

        sut.triggerEvent(CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected)
        advanceUntilIdle()

        assertThat(events).containsExactly(CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected)
    }

    @Test
    fun `triggerEvent ignores an event from another hierarchy`() = runTest(testDispatcher.scheduler) {
        val events = collectEvents()

        sut.triggerEvent(AlarmEvent.NoAction)
        advanceUntilIdle()

        assertThat(events).isEmpty()
    }

    // ---- observePatchInfo ---------------------------------------------------------------------

    @Test
    fun `observePatchInfo marks the needle as inserted`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))

        assertThat(sut.isNeedleInsert.value).isTrue()
    }

    @Test
    fun `observePatchInfo clears the needle flag when the patch reports it withdrawn`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))
        assertThat(sut.isNeedleInsert.value).isTrue()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 0)))
        assertThat(sut.isNeedleInsert.value).isFalse()
    }

    @Test
    fun `observePatchInfo keeps the first insert timestamp across repeated inserted reports`() {
        // The patch re-reports checkNeedle=true on every poll; the insert instant must be latched on
        // the first one (`if (needleInsertedAtMs == null)`) so the needle-to-basal delay is measured
        // from the real insertion, not from the latest poll.
        sut.observePatchInfo()
        givenReadyToSetBasal()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))

        sut.startSetBasal()
        testDispatcher.scheduler.runCurrent()

        // Still inside the latched delay window -> parked, not programmed.
        assertThat(sut.isNeedleInsert.value).isTrue()
        assertThat(sut.uiState.value).isEqualTo(UiState.Loading)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `observePatchInfo withdrawal cancels a parked delayed basal job`() {
        // Park a delayed startSetBasal, then report the needle withdrawn. The withdrawal clears the
        // insert instant AND cancels the job; if it only cleared the instant, the still-armed job
        // would fire below and program the basal against a withdrawn needle.
        givenReadyToSetBasal()
        val ok = enactResult(true)
        whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
        sut.observePatchInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))

        sut.startSetBasal()
        testDispatcher.scheduler.runCurrent()
        assertThat(sut.uiState.value).isEqualTo(UiState.Loading)

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 0)))
        // Advance past NEEDLE_TO_BASAL_DELAY_MS by a bounded amount: a cancelled job never fires, an
        // armed one would re-enter startSetBasal with a null insert instant and reach the queue.
        testDispatcher.scheduler.advanceTimeBy(11_000L)
        testDispatcher.scheduler.runCurrent()

        assertThat(sut.isNeedleInsert.value).isFalse()
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `observePatchInfo ignores an empty patch info`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.empty())

        assertThat(sut.isNeedleInsert.value).isFalse()
        verify(carelevoAlarmInfoUseCase, never()).upsertAlarm(any())
    }

    @Test
    fun `observePatchInfo raises a needle-insertion alarm and reports the failure at three attempts`() =
        runTest(testDispatcher.scheduler) {
            whenever(carelevoAlarmInfoUseCase.upsertAlarm(any())).thenReturn(Completable.complete())
            val events = collectEvents()
            sut.observePatchInfo()

            patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 3)))
            advanceUntilIdle()

            val captor = argumentCaptor<CarelevoAlarmInfo>()
            verify(carelevoAlarmInfoUseCase).upsertAlarm(captor.capture())
            assertThat(captor.firstValue.cause).isEqualTo(AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR)
            assertThat(captor.firstValue.alarmType).isEqualTo(AlarmType.WARNING)
            assertThat(captor.firstValue.isAcknowledged).isFalse()
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.CheckNeedleFailed(3))
        }

    @Test
    fun `observePatchInfo does not alarm below three needle failures`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 2)))

        verify(carelevoAlarmInfoUseCase, never()).upsertAlarm(any())
    }

    @Test
    fun `observePatchInfo logs when the alarm upsert fails`() {
        whenever(carelevoAlarmInfoUseCase.upsertAlarm(any())).thenReturn(Completable.error(RuntimeException("boom")))
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 3)))

        verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>())
    }

    // ---- startCheckNeedle ---------------------------------------------------------------------

    @Test
    fun `startCheckNeedle without bluetooth reports it and never reaches the queue`() =
        runTest(testDispatcher.scheduler) {
            whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
            val events = collectEvents()

            sut.startCheckNeedle()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verifyBlocking(commandQueue, never()) { customCommand(any()) }
        }

    @Test
    fun `startCheckNeedle success runs the check on the queue and reports completion`() =
        runTest(testDispatcher.scheduler) {
            whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            val events = collectEvents()

            sut.startCheckNeedle()
            advanceUntilIdle()

            verifyBlocking(commandQueue) { customCommand(any<CmdNeedleCheck>()) }
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.CheckNeedleComplete(true))
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        }

    @Test
    fun `startCheckNeedle failure with a recorded fail count reports CheckNeedleFailed`() =
        runTest(testDispatcher.scheduler) {
            whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
            val fail = enactResult(false)
            whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
            // A failed check leaves the count on the patch.
            patchInfoSubject.onNext(Optional.of(patchInfo(needleFailedCount = 2)))
            val events = collectEvents()

            sut.startCheckNeedle()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.CheckNeedleFailed(2))
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        }

    @Test
    fun `startCheckNeedle failure without a fail count reports a generic error`() =
        runTest(testDispatcher.scheduler) {
            whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
            val fail = enactResult(false)
            whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
            // No patch info at all -> the queue never reached the patch -> no count to report.
            val events = collectEvents()

            sut.startCheckNeedle()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.CheckNeedleError)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        }

    // ---- startSetBasal: guards ----------------------------------------------------------------

    @Test
    fun `startSetBasal without bluetooth reports it and returns to Idle`() =
        runTest(testDispatcher.scheduler) {
            whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verifyBlocking(commandQueue, never()) { customCommand(any()) }
        }

    @Test
    fun `startSetBasal without a profile reports it and returns to Idle`() =
        runTest(testDispatcher.scheduler) {
            whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
            profileSubject.onNext(Optional.empty())
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.ShowMessageProfileNotSet)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verifyBlocking(commandQueue, never()) { customCommand(any()) }
        }

    @Test
    fun `startSetBasal waits out the needle-to-basal delay before touching the queue`() {
        // A needle inserted just now leaves ~10s of NEEDLE_TO_BASAL_DELAY_MS to run: the call must
        // park in a delayed job (Loading) instead of programming the basal immediately.
        sut.observePatchInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))

        sut.startSetBasal()
        // runCurrent() flushes the setUiState launch but deliberately does NOT advance virtual time,
        // so the parked delayed job never fires. (advanceUntilIdle would re-enter startSetBasal,
        // which re-arms the job because the *wall* clock the guard reads has not moved.)
        testDispatcher.scheduler.runCurrent()

        assertThat(sut.uiState.value).isEqualTo(UiState.Loading)
        verify(carelevoPatch, never()).isBluetoothEnabled()
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `startSetBasal runs immediately once the needle has been withdrawn`() =
        runTest(testDispatcher.scheduler) {
            // Insert then withdraw clears the insert instant, so the needle-to-basal delay guard is
            // skipped entirely and the basal is programmed on the spot (no parked job).
            sut.observePatchInfo()
            patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))
            patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 0)))
            givenReadyToSetBasal()
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            whenever {
                pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }.thenReturn(true)
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            verifyBlocking(commandQueue) { customCommand(any<CmdSetBasal>()) }
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.SetBasalComplete)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        }

    // ---- startSetBasal: success bookkeeping ---------------------------------------------------

    @Test
    fun `startSetBasal success syncs the pump and records both therapy events`() =
        runTest(testDispatcher.scheduler) {
            givenReadyToSetBasal()
            patchInfoSubject.onNext(Optional.of(patchInfo(manufactureNumber = "SN-1")))
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            whenever {
                pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }.thenReturn(true)
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            verifyBlocking(commandQueue) { customCommand(any<CmdSetBasal>()) }
            verify(pumpSync).connectNewPump(true)
            verifyBlocking(pumpSync) {
                insertTherapyEventIfNewWithTimestamp(
                    any(), eq(TE.Type.CANNULA_CHANGE), anyOrNull(), anyOrNull(),
                    eq(PumpType.CAREMEDI_CARELEVO), eq("SN-1")
                )
            }
            verifyBlocking(pumpSync) {
                insertTherapyEventIfNewWithTimestamp(
                    any(), eq(TE.Type.INSULIN_CHANGE), anyOrNull(), anyOrNull(),
                    eq(PumpType.CAREMEDI_CARELEVO), eq("SN-1")
                )
            }
            // Site rotation skipped -> no therapy event is looked up or patched.
            verifyBlocking(persistenceLayer, never()) { getTherapyEventDataFromToTime(any(), any()) }
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.SetBasalComplete)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        }

    @Test
    fun `startSetBasal falls back to an empty serial when the patch info has none`() =
        runTest(testDispatcher.scheduler) {
            givenReadyToSetBasal()
            // No patch info cached -> manufactureNumber elvis -> "".
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            whenever {
                pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }.thenReturn(true)

            sut.startSetBasal()
            advanceUntilIdle()

            verifyBlocking(pumpSync) {
                insertTherapyEventIfNewWithTimestamp(
                    any(), eq(TE.Type.CANNULA_CHANGE), anyOrNull(), anyOrNull(), any(), eq("")
                )
            }
        }

    @Test
    fun `startSetBasal success patches the chosen site location onto the cannula-change event`() =
        runTest(testDispatcher.scheduler) {
            givenReadyToSetBasal()
            whenever(carelevoPatch.sitePlacementLocation).thenReturn(TE.Location.SIDE_LEFT_UPPER_ARM)
            whenever(carelevoPatch.sitePlacementArrow).thenReturn(TE.Arrow.DOWN)
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            whenever {
                pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }.thenReturn(true)
            whenever { persistenceLayer.getTherapyEventDataFromToTime(any(), any()) }
                .thenReturn(listOf(therapyEvent(TE.Type.CANNULA_CHANGE)))
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            val captor = argumentCaptor<TE>()
            verifyBlocking(persistenceLayer) { insertOrUpdateTherapyEvent(captor.capture()) }
            assertThat(captor.firstValue.type).isEqualTo(TE.Type.CANNULA_CHANGE)
            assertThat(captor.firstValue.location).isEqualTo(TE.Location.SIDE_LEFT_UPPER_ARM)
            assertThat(captor.firstValue.arrow).isEqualTo(TE.Arrow.DOWN)
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.SetBasalComplete)
        }

    @Test
    fun `startSetBasal does not patch a site location when no cannula-change event is found`() =
        runTest(testDispatcher.scheduler) {
            givenReadyToSetBasal()
            whenever(carelevoPatch.sitePlacementLocation).thenReturn(TE.Location.SIDE_LEFT_UPPER_ARM)
            whenever(carelevoPatch.sitePlacementArrow).thenReturn(TE.Arrow.DOWN)
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            whenever {
                pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }.thenReturn(true)
            // Only a non-cannula event in the window -> firstOrNull{} finds nothing.
            whenever { persistenceLayer.getTherapyEventDataFromToTime(any(), any()) }
                .thenReturn(listOf(therapyEvent(TE.Type.INSULIN_CHANGE)))

            sut.startSetBasal()
            advanceUntilIdle()

            verifyBlocking(persistenceLayer, never()) { insertOrUpdateTherapyEvent(any()) }
        }

    @Test
    fun `startSetBasal still completes when patching the site location fails`() =
        runTest(testDispatcher.scheduler) {
            givenReadyToSetBasal()
            whenever(carelevoPatch.sitePlacementLocation).thenReturn(TE.Location.SIDE_LEFT_UPPER_ARM)
            whenever(carelevoPatch.sitePlacementArrow).thenReturn(TE.Arrow.DOWN)
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            whenever {
                pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }.thenReturn(true)
            whenever { persistenceLayer.getTherapyEventDataFromToTime(any(), any()) }
                .thenThrow(RuntimeException("db down"))
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            // Site location is optional bookkeeping: the failure is swallowed, basal still succeeded.
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.SetBasalComplete)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        }

    @Test
    fun `startSetBasal success retries each therapy event once when the first insert is rejected`() =
        runTest(testDispatcher.scheduler) {
            givenReadyToSetBasal()
            patchInfoSubject.onNext(Optional.of(patchInfo(manufactureNumber = "SN-1")))
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            // Never accepted -> both inserts take the SystemClock.sleep recovery path (Robolectric).
            whenever {
                pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }.thenReturn(false)
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            verifyBlocking(pumpSync, times(2)) {
                insertTherapyEventIfNewWithTimestamp(
                    any(), eq(TE.Type.CANNULA_CHANGE), anyOrNull(), anyOrNull(), any(), any()
                )
            }
            verifyBlocking(pumpSync, times(2)) {
                insertTherapyEventIfNewWithTimestamp(
                    any(), eq(TE.Type.INSULIN_CHANGE), anyOrNull(), anyOrNull(), any(), any()
                )
            }
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.SetBasalComplete)
        }

    @Test
    fun `startSetBasal failure reports it and skips all pump bookkeeping`() =
        runTest(testDispatcher.scheduler) {
            givenReadyToSetBasal()
            val fail = enactResult(false)
            whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
            val events = collectEvents()

            sut.startSetBasal()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.SetBasalFailed)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verify(pumpSync, never()).connectNewPump(any())
            verifyBlocking(pumpSync, never()) {
                insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())
            }
        }

    // ---- startDiscardProcess ------------------------------------------------------------------

    @Test
    fun `startDiscardProcess with no patch completes without touching the queue`() =
        runTest(testDispatcher.scheduler) {
            patchStateSubject.onNext(Optional.of(PatchState.NotConnectedNotBooting))
            val events = collectEvents()

            sut.startDiscardProcess()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.DiscardComplete)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verifyBlocking(commandQueue, never()) { customCommand(any()) }
        }

    @Test
    fun `startDiscardProcess with an unknown patch state completes without touching the queue`() =
        runTest(testDispatcher.scheduler) {
            // patchState never emitted -> value is null -> same branch as NotConnectedNotBooting.
            val events = collectEvents()

            sut.startDiscardProcess()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.DiscardComplete)
            verifyBlocking(commandQueue, never()) { customCommand(any()) }
        }

    @Test
    fun `startDiscardProcess success discards through the queue`() =
        runTest(testDispatcher.scheduler) {
            patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
            val ok = enactResult(true)
            whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
            val events = collectEvents()

            sut.startDiscardProcess()
            advanceUntilIdle()

            verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.DiscardComplete)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verify(patchForceDiscardUseCase, never()).execute()
        }

    @Test
    fun `startDiscardProcess falls back to a force discard when the queue cannot reach the patch`() =
        runTest(testDispatcher.scheduler) {
            patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
            val fail = enactResult(false)
            whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
            whenever(patchForceDiscardUseCase.execute())
                .thenReturn(
                    Single.just<ResponseResult<CarelevoUseCaseResponse>>(
                        ResponseResult.Success<CarelevoUseCaseResponse>(null)
                    )
                )
            val events = collectEvents()

            sut.startDiscardProcess()
            advanceUntilIdle()

            verify(aapsLogger).error(eq(LTag.PUMPCOMM), any<String>())
            verify(carelevoPatch).discardTeardown()
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.DiscardComplete)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        }

    @Test
    fun `startDiscardProcess reports failure when the force discard errors`() =
        runTest(testDispatcher.scheduler) {
            patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
            val fail = enactResult(false)
            whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
            whenever(patchForceDiscardUseCase.execute())
                .thenReturn(
                    Single.just<ResponseResult<CarelevoUseCaseResponse>>(ResponseResult.Error(RuntimeException("boom")))
                )
            val events = collectEvents()

            sut.startDiscardProcess()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.DiscardFailed)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verify(carelevoPatch, never()).discardTeardown()
        }

    @Test
    fun `startDiscardProcess reports failure when the force discard returns a failure response`() =
        runTest(testDispatcher.scheduler) {
            patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
            val fail = enactResult(false)
            whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
            whenever(patchForceDiscardUseCase.execute())
                .thenReturn(Single.just<ResponseResult<CarelevoUseCaseResponse>>(ResponseResult.Failure("nope")))
            val events = collectEvents()

            sut.startDiscardProcess()
            advanceUntilIdle()

            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.DiscardFailed)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verify(carelevoPatch, never()).discardTeardown()
        }

    @Test
    fun `startDiscardProcess reports failure when the force discard stream errors`() =
        runTest(testDispatcher.scheduler) {
            patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
            val fail = enactResult(false)
            whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
            whenever(patchForceDiscardUseCase.execute())
                .thenReturn(Single.error<ResponseResult<CarelevoUseCaseResponse>>(RuntimeException("boom")))
            val events = collectEvents()

            sut.startDiscardProcess()
            advanceUntilIdle()

            // doOnError branch of the force-discard chain.
            assertThat(events).containsExactly(CarelevoConnectNeedleEvent.DiscardFailed)
            assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
            verify(carelevoPatch, never()).discardTeardown()
        }

    // ---- onCleared ----------------------------------------------------------------------------

    /** onCleared is protected on ViewModel and the VM is final, so drive it reflectively. */
    private fun clearViewModel() {
        val onCleared = sut.javaClass.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(sut)
    }

    @Test
    fun `onCleared disposes the patch-info subscription so later emissions are ignored`() {
        sut.observePatchInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))
        assertThat(sut.isNeedleInsert.value).isTrue()

        clearViewModel()
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 0)))

        // Subscription disposed on teardown -> the withdrawal never reaches the flow.
        assertThat(sut.isNeedleInsert.value).isTrue()
    }

    @Test
    fun `onCleared does not alarm on a post-teardown needle failure`() {
        sut.observePatchInfo()

        clearViewModel()
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 3)))

        verify(carelevoAlarmInfoUseCase, never()).upsertAlarm(any())
    }

    @Test
    fun `onCleared tears down a parked delayed basal job without throwing`() {
        // Coverage of the delayedStartBasalJob.cancel() line. Deliberately no queue assertion: the
        // delay guard reads the wall clock, which the virtual scheduler cannot advance, so an
        // un-cancelled job would merely re-park — the cancel is not observable through the queue.
        givenReadyToSetBasal()
        sut.observePatchInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true, needleFailedCount = 0)))
        sut.startSetBasal()
        testDispatcher.scheduler.runCurrent()
        assertThat(sut.uiState.value).isEqualTo(UiState.Loading)

        clearViewModel()

        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }
}
