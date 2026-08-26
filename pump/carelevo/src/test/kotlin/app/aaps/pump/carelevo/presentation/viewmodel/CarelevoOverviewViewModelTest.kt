package app.aaps.pump.carelevo.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.command.CmdPumpResume
import app.aaps.pump.carelevo.command.CmdPumpStop
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoDeleteInfusionInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.model.CarelevoDeleteInfusionRequestModel
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoOverviewEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.joda.time.DateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unit tests for [CarelevoOverviewViewModel] — the Carelevo overview screen's state assembly, the
 * pump stop/resume/discard flows and the status refresh path.
 *
 * **Robolectric, not plain JVM.** The ViewModel publishes most of its screen data through
 * [androidx.lifecycle.MutableLiveData]. `LiveData.setValue` asserts it runs on the main thread via
 * `ArchTaskExecutor` → `Looper.getMainLooper()`, which returns `null` under a plain JVM unit test
 * (`isReturnDefaultValues`) and NPEs. Under [RobolectricTestRunner] the test thread *is* the main
 * looper's thread, so every LiveData write executes for real — matching the sibling
 * `CarelevoAlarmViewModelTest`/`CarelevoAlarmNotifierTest` in this module. The real
 * [Context] is only handed to the shared `PumpCommunicationStatus`, which never touches it while its
 * two [RxBus] flows stay empty.
 *
 * **Dispatcher strategy.** `Dispatchers.setMain(UnconfinedTestDispatcher())` makes `viewModelScope`
 * eager: `triggerEvent`, `setUiState`, `startDiscardProcess`, `startPumpStopProcess`,
 * `startPumpResume` and `refreshPatchInfusionInfo` all run their `launch { }` bodies in-line, so the
 * terminal state can be asserted straight after the call. The suspend [CommandQueue]/[PumpSync]
 * members are Mockito stubs and never really suspend, so nothing needs virtual time to be advanced.
 * Deliberately **no `advanceUntilIdle()`**: `overviewUiState` combines an endless `tickerFlow(30 s)`
 * whose `delay` is virtual, so advancing to idle would never terminate. The ticker's first emission
 * is immediate, which is all the combine needs.
 *
 * Rx is pinned to [Schedulers.trampoline] through the mocked [AapsSchedulers], so `observePatchInfo`
 * / `observePatchState` / `observeInfusionInfo` / `observeProfile` deliver synchronously on the test
 * thread. Their sources are real [BehaviorSubject]s, which also serve the `.value` reads the VM does
 * on the same properties.
 *
 * [ResourceHelper.gs] is answered with a deterministic `S<id>(arg,arg)` encoding ([s]) so assembled
 * row labels/values can be asserted exactly without depending on translations.
 *
 * The `secondTick` → `clearExpiredInfusions` loop in `init` runs on a real [Dispatchers.Default]
 * clock (its `delay` is NOT virtual), so the two tests that cover expiry use Mockito's
 * [timeout]/[after] verification modes rather than fake time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class CarelevoOverviewViewModelTest {

    // REAL application context — only stored by PumpCommunicationStatus, never dereferenced here.
    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var rh: ResourceHelper
    private lateinit var pumpSync: PumpSync
    private lateinit var dateUtil: DateUtil
    private lateinit var commandQueue: CommandQueue
    private lateinit var aapsLogger: AAPSLogger
    private lateinit var carelevoPatch: CarelevoPatch
    private lateinit var aapsSchedulers: AapsSchedulers
    private lateinit var patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase
    private lateinit var deleteInfusionInfoUseCase: CarelevoDeleteInfusionInfoUseCase
    private lateinit var rxBus: RxBus
    private lateinit var bleSession: CarelevoBleSession

    private lateinit var sut: CarelevoOverviewViewModel

    // Real subjects: the VM both subscribes to these AND reads their `.value` synchronously.
    private val patchInfoSubject: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>> = BehaviorSubject.create()
    private val patchStateSubject: BehaviorSubject<Optional<PatchState>> = BehaviorSubject.create()
    private val infusionSubject: BehaviorSubject<Optional<CarelevoInfusionInfoDomainModel>> = BehaviorSubject.create()
    private val profileSubject: BehaviorSubject<Optional<Profile>> = BehaviorSubject.create()

    // Live connection flows the VM reads from CarelevoBleSession; mutate them in a test to drive the rows.
    private val connectedFlow = MutableStateFlow(false)
    private val lastConnectedFlow = MutableStateFlow(0L)

    private val events = CopyOnWriteArrayList<Event>()
    private lateinit var collectorScope: CoroutineScope

    /** Fixed "now" fed to the VM through [DateUtil.now]; the expiry maths is derived from it. */
    private val nowMillis = 1_752_000_000_000L
    private val dayMillis = 24L * 60L * 60L * 1000L

    // ---- helpers ------------------------------------------------------------------------------

    /** Mirror of the stubbed [ResourceHelper.gs] answers — lets tests assert exact assembled text. */
    private fun s(id: Int): String = "S$id"
    private fun s(id: Int, vararg args: Any?): String = "S$id(${args.joinToString(",")})"

    private fun local(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    private fun uiFormat(ldt: LocalDateTime): String = ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun patchInfo(
        manufactureNumber: String? = "SN-0001",
        firmwareVersion: String? = "T168",
        bootDateTimeUtcMillis: Long? = nowMillis - 2 * dayMillis,
        bootDateTime: String? = null,
        insulinAmount: Int? = 200,
        insulinRemain: Double? = 150.5,
        checkSafety: Boolean? = true,
        checkNeedle: Boolean? = true,
        needleFailedCount: Int? = 0,
        isStopped: Boolean? = false,
        infusedTotalBasalAmount: Double? = 1.234,
        infusedTotalBolusAmount: Double? = 2.345,
        mode: Int? = 1
    ): CarelevoPatchInfoDomainModel = CarelevoPatchInfoDomainModel(
        address = "AA:BB:CC:DD:EE:FF",
        manufactureNumber = manufactureNumber,
        firmwareVersion = firmwareVersion,
        bootDateTime = bootDateTime,
        bootDateTimeUtcMillis = bootDateTimeUtcMillis,
        insulinAmount = insulinAmount,
        insulinRemain = insulinRemain,
        checkSafety = checkSafety,
        checkNeedle = checkNeedle,
        needleFailedCount = needleFailedCount,
        isStopped = isStopped,
        infusedTotalBasalAmount = infusedTotalBasalAmount,
        infusedTotalBolusAmount = infusedTotalBolusAmount,
        mode = mode
    )

    private fun tempBasal(
        speed: Double? = 1.2,
        durationMin: Int? = null,
        createdAt: DateTime = DateTime.now()
    ) = CarelevoTempBasalInfusionInfoDomainModel(
        infusionId = "temp", address = "A", mode = 2, createdAt = createdAt,
        speed = speed, infusionDurationMin = durationMin
    )

    private fun immeBolus(
        durationSeconds: Int? = null,
        createdAt: DateTime = DateTime.now()
    ) = CarelevoImmeBolusInfusionInfoDomainModel(
        infusionId = "imme", address = "A", mode = 3, createdAt = createdAt,
        volume = 1.0, infusionDurationSeconds = durationSeconds
    )

    private fun extendBolus(
        durationMin: Int? = null,
        createdAt: DateTime = DateTime.now()
    ) = CarelevoExtendBolusInfusionInfoDomainModel(
        infusionId = "extend", address = "A", mode = 5, createdAt = createdAt,
        volume = 2.0, speed = 0.5, infusionDurationMin = durationMin
    )

    /** Build the stubbed result BEFORE the whenever() that returns it (UnfinishedStubbingException). */
    private fun stubCustomCommand(success: Boolean) {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        whenever { commandQueue.customCommand(any()) }.thenReturn(result)
    }

    private fun stubCancelTempBasal(success: Boolean) {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        // 2 explicit matchers: `autoForced` has a default value, so the mock records both args.
        whenever { commandQueue.cancelTempBasal(any(), any()) }.thenReturn(result)
    }

    private fun stubCancelExtended(success: Boolean) {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        whenever { commandQueue.cancelExtended() }.thenReturn(result)
    }

    private fun infoRows(): List<PumpInfoRow> = sut.overviewUiState.value.infoRows.map { it as PumpInfoRow }

    // ---- setup --------------------------------------------------------------------------------

    @Before
    fun setUp() {
        rh = mock()
        pumpSync = mock()
        dateUtil = mock()
        commandQueue = mock()
        aapsLogger = mock()
        carelevoPatch = mock()
        aapsSchedulers = mock()
        patchForceDiscardUseCase = mock()
        deleteInfusionInfoUseCase = mock()
        rxBus = mock()
        bleSession = mock()

        // Main must be a test dispatcher BEFORE construction: viewModelScope resolves it on creation.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // `clearInfusionInfo` subscribes with both arms, but an errored patchInfo/profile stream ends
        // in `subscribe { }` consumers without an onError arm → swallow the global Rx rethrow.
        RxJavaPlugins.setErrorHandler { }

        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(dateUtil.now()).thenReturn(nowMillis)

        // PumpCommunicationStatus subscribes to both in its init; empty flows keep banner/queue null.
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())

        whenever(carelevoPatch.patchInfo).thenReturn(patchInfoSubject)
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject)
        whenever(carelevoPatch.infusionInfo).thenReturn(infusionSubject)
        whenever(carelevoPatch.profile).thenReturn(profileSubject)

        whenever(bleSession.connected).thenReturn(connectedFlow)
        whenever(bleSession.lastConnectedAt).thenReturn(lastConnectedFlow)

        // Deterministic, translation-independent resource text (see `s`).
        whenever(rh.gs(any<Int>())).thenAnswer { inv -> "S${inv.getArgument<Int>(0)}" }
        whenever(rh.gs(any<Int>(), anyOrNull()))
            .thenAnswer { inv -> "S${inv.getArgument<Int>(0)}(${inv.getArgument<Any?>(1)})" }
        whenever(rh.gs(any<Int>(), anyOrNull(), anyOrNull()))
            .thenAnswer { inv -> "S${inv.getArgument<Int>(0)}(${inv.getArgument<Any?>(1)},${inv.getArgument<Any?>(2)})" }
        whenever(rh.gs(any<Int>(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer { inv ->
                "S${inv.getArgument<Int>(0)}(${inv.getArgument<Any?>(1)},${inv.getArgument<Any?>(2)},${inv.getArgument<Any?>(3)})"
            }

        // Default: the delete use case succeeds. The `init` second-tick may reach it at any moment,
        // and an unstubbed (null) Single would NPE on a background thread.
        whenever(deleteInfusionInfoUseCase.execute(any()))
            .thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))

        sut = CarelevoOverviewViewModel(
            rh = rh,
            pumpSync = pumpSync,
            dateUtil = dateUtil,
            commandQueue = commandQueue,
            aapsLogger = aapsLogger,
            carelevoPatch = carelevoPatch,
            bleSession = bleSession,
            aapsSchedulers = aapsSchedulers,
            patchForceDiscardUseCase = patchForceDiscardUseCase,
            carelevoDeleteInfusionInfoUseCase = deleteInfusionInfoUseCase,
            rxBus = rxBus,
            context = context
        )

        collectorScope = CoroutineScope(Dispatchers.Unconfined)
        // `event` is a consume-once EventFlow → exactly one collector may own it.
        collectorScope.launch { sut.event.collect { events.add(it) } }
        // overviewUiState is stateIn(WhileSubscribed) → it only recomputes while someone subscribes.
        collectorScope.launch { sut.overviewUiState.collect { } }
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        // `onCleared` is protected, so cancel the scope directly: without this the endless second-tick
        // collector outlives the test and resumes onto a dispatcher that no longer exists. Join rather
        // than just cancel — cancel() only *requests* it, and an in-flight tick still resuming while
        // resetMain() swaps the dispatcher out trips "Main is used concurrently with setting it".
        runBlocking { sut.viewModelScope.coroutineContext.job.cancelAndJoin() }
        RxJavaPlugins.reset()
        Dispatchers.resetMain()
    }

    // ---- initial state ------------------------------------------------------------------------

    @Test
    fun `uiState starts Idle`() {
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `patchState starts NotConnectedNotBooting`() {
        assertThat(sut.patchState.value).isEqualTo(PatchState.NotConnectedNotBooting)
    }

    @Test
    fun `isCheckScreen starts null`() {
        assertThat(sut.isCheckScreen.value).isNull()
    }

    @Test
    fun `isPumpStop starts false`() {
        assertThat(sut.isPumpStop.value).isFalse()
    }

    @Test
    fun `hasUnacknowledgedAlarms starts false and initUnacknowledgedAlarms resets it`() {
        assertThat(sut.hasUnacknowledgedAlarms.value).isFalse()

        sut.initUnacknowledgedAlarms()

        assertThat(sut.hasUnacknowledgedAlarms.value).isFalse()
    }

    @Test
    fun `setIsCreated flips the created latch`() {
        assertThat(sut.isCreated).isFalse()

        sut.setIsCreated(true)
        assertThat(sut.isCreated).isTrue()

        sut.setIsCreated(false)
        assertThat(sut.isCreated).isFalse()
    }

    // ---- parseBootDateTime(String?) -----------------------------------------------------------

    @Test
    fun `parseBootDateTime returns null for a null raw string`() {
        assertThat(sut.parseBootDateTime(null as String?)).isNull()
    }

    @Test
    fun `parseBootDateTime returns null for a blank raw string`() {
        assertThat(sut.parseBootDateTime("   ")).isNull()
    }

    @Test
    fun `parseBootDateTime parses the yyMMddHHmm patch format`() {
        assertThat(sut.parseBootDateTime("2601011230")).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 30))
    }

    @Test
    fun `parseBootDateTime returns null for an unparseable raw string`() {
        assertThat(sut.parseBootDateTime("not-a-date")).isNull()
    }

    // ---- parseBootDateTime(Long?) -------------------------------------------------------------

    @Test
    fun `parseBootDateTime returns null for null utc millis`() {
        assertThat(sut.parseBootDateTime(null as Long?)).isNull()
    }

    @Test
    fun `parseBootDateTime converts utc millis into the system zone`() {
        assertThat(sut.parseBootDateTime(nowMillis)).isEqualTo(local(nowMillis))
    }

    // ---- observePatchInfo ---------------------------------------------------------------------

    @Test
    fun `observePatchInfo maps a report onto every overview field`() {
        val bootMillis = nowMillis - 2 * dayMillis
        val bootLdt = local(bootMillis)
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(bootDateTimeUtcMillis = bootMillis)))

        assertThat(sut.serialNumber.value).isEqualTo("SN-0001")
        assertThat(sut.lotNumber.value).isEqualTo("T168")
        assertThat(sut.bootDateTime.value).isEqualTo(uiFormat(bootLdt))
        assertThat(sut.expirationTime.value).isEqualTo(uiFormat(bootLdt.plusDays(7)))
        // 1.234 + 2.345 rounded HALF_UP to 2dp each = 1.23 + 2.35 = 3.58
        assertThat(sut.totalInsulinAmount.value).isEqualTo(3.58)
        assertThat(sut.isPumpStop.value).isFalse()
        assertThat(sut.insulinRemains.value).isEqualTo(
            s(
                R.string.carelevo_insulin_remain_value,
                s(R.string.common_label_unit_value_dose_with_space, 150.5),
                s(R.string.common_label_unit_value_dose_with_space, 200)
            )
        )
        val expectedRemain = ChronoUnit.MINUTES.between(local(nowMillis), bootLdt.plusDays(7)).toInt()
        assertThat(sut.runningRemainMinutes.value).isEqualTo(expectedRemain)
        assertThat(expectedRemain).isGreaterThan(0)
    }

    @Test
    fun `observePatchInfo falls back to the yyMMddHHmm boot string when utc millis are missing`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(bootDateTimeUtcMillis = null, bootDateTime = "2601011230")))

        assertThat(sut.bootDateTime.value).isEqualTo("2026-01-01 12:30")
        assertThat(sut.expirationTime.value).isEqualTo("2026-01-08 12:30")
    }

    @Test
    fun `observePatchInfo leaves boot fields empty when no boot time is known at all`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(bootDateTimeUtcMillis = null, bootDateTime = null)))

        assertThat(sut.bootDateTime.value).isEqualTo("")
        assertThat(sut.expirationTime.value).isEqualTo("")
        assertThat(sut.runningRemainMinutes.value).isEqualTo(0)
    }

    @Test
    fun `observePatchInfo blanks the insulin remain text when either amount is unknown`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(insulinRemain = null)))

        assertThat(sut.insulinRemains.value).isEqualTo("")
    }

    @Test
    fun `observePatchInfo defaults missing infused totals and stop flag`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(
            Optional.of(
                patchInfo(
                    manufactureNumber = null, firmwareVersion = null,
                    infusedTotalBasalAmount = null, infusedTotalBolusAmount = null, isStopped = null
                )
            )
        )

        assertThat(sut.serialNumber.value).isEqualTo("")
        assertThat(sut.lotNumber.value).isEqualTo("")
        assertThat(sut.totalInsulinAmount.value).isEqualTo(0.0)
        assertThat(sut.isPumpStop.value).isFalse()
    }

    @Test
    fun `observePatchInfo reports a positive overdue countdown once the patch is past expiry`() {
        val bootMillis = nowMillis - 8 * dayMillis
        val bootLdt = local(bootMillis)
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(bootDateTimeUtcMillis = bootMillis)))

        // now is after boot+7d → the countdown flips to "time since expiry" and expiry gains 12 h.
        val expectedRemain = ChronoUnit.MINUTES.between(bootLdt.plusDays(7), local(nowMillis)).toInt()
        assertThat(sut.runningRemainMinutes.value).isEqualTo(expectedRemain)
        assertThat(expectedRemain).isGreaterThan(0)
        assertThat(sut.expirationTime.value).isEqualTo(uiFormat(bootLdt.plusDays(7).plusHours(12)))
    }

    @Test
    fun `observePatchInfo skips a null report and clears the check screen`() {
        sut.observePatchInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo(checkSafety = null)))
        assertThat(sut.isCheckScreen.value).isEqualTo(CarelevoScreenType.SAFETY_CHECK)

        patchInfoSubject.onNext(Optional.empty())

        assertThat(sut.isCheckScreen.value).isNull()
        // The empty report never reaches updateState — the last known values survive.
        assertThat(sut.serialNumber.value).isEqualTo("SN-0001")
    }

    @Test
    fun `observePatchInfo survives an errored stream`() {
        sut.observePatchInfo()

        patchInfoSubject.onError(RuntimeException("boom"))

        assertThat(sut.isCheckScreen.value).isNull()
        assertThat(sut.serialNumber.value).isNull()
    }

    // ---- updateCheckScreen branches -----------------------------------------------------------

    @Test
    fun `check screen asks for needle insertion while retries remain`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 2)))

        assertThat(sut.isCheckScreen.value).isEqualTo(CarelevoScreenType.NEEDLE_INSERTION)
    }

    @Test
    fun `check screen clears once the needle retry budget is spent`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = 3)))

        assertThat(sut.isCheckScreen.value).isNull()
    }

    @Test
    fun `check screen clears when the needle failure count is unknown`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false, needleFailedCount = null)))

        assertThat(sut.isCheckScreen.value).isNull()
    }

    @Test
    fun `check screen asks for the safety check when it has not run yet`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkSafety = null, checkNeedle = null)))

        assertThat(sut.isCheckScreen.value).isEqualTo(CarelevoScreenType.SAFETY_CHECK)
    }

    @Test
    fun `check screen asks for the safety check when it passed but the needle is unknown`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkSafety = true, checkNeedle = null)))

        assertThat(sut.isCheckScreen.value).isEqualTo(CarelevoScreenType.SAFETY_CHECK)
    }

    @Test
    fun `check screen is clear for a fully activated patch`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkSafety = true, checkNeedle = true)))

        assertThat(sut.isCheckScreen.value).isNull()
    }

    @Test
    fun `check screen is clear when the safety check failed but the needle is known`() {
        sut.observePatchInfo()

        patchInfoSubject.onNext(Optional.of(patchInfo(checkSafety = false, checkNeedle = true)))

        assertThat(sut.isCheckScreen.value).isNull()
    }

    // ---- observePatchState --------------------------------------------------------------------

    @Test
    fun `observePatchState publishes a booted state and pulls the basal rate from the profile`() {
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(1.75)
        profileSubject.onNext(Optional.of(profile))
        sut.observePatchState()

        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        assertThat(sut.patchState.value).isEqualTo(PatchState.ConnectedBooted)
        assertThat(sut.basalRate.value).isEqualTo(1.75)
    }

    @Test
    fun `observePatchState falls back to a zero basal rate when no profile is set`() {
        sut.observePatchState()

        patchStateSubject.onNext(Optional.of(PatchState.NotConnectedBooted))

        assertThat(sut.patchState.value).isEqualTo(PatchState.NotConnectedBooted)
        assertThat(sut.basalRate.value).isEqualTo(0.0)
    }

    @Test
    fun `observePatchState wipes every overview field when the patch is gone`() {
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(1.75)
        profileSubject.onNext(Optional.of(profile))
        sut.observePatchInfo()
        sut.observePatchState()
        sut.observeInfusionInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = true)))
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasal(speed = 2.5))))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        assertThat(sut.basalRate.value).isEqualTo(1.75)
        assertThat(sut.tempBasalRate.value).isEqualTo(2.5)

        patchStateSubject.onNext(Optional.of(PatchState.NotConnectedNotBooting))

        assertThat(sut.patchState.value).isEqualTo(PatchState.NotConnectedNotBooting)
        assertThat(sut.serialNumber.value).isEqualTo("")
        assertThat(sut.lotNumber.value).isEqualTo("")
        assertThat(sut.bootDateTime.value).isEqualTo("")
        assertThat(sut.expirationTime.value).isEqualTo("")
        assertThat(sut.insulinRemains.value).isEqualTo("")
        assertThat(sut.totalInsulinAmount.value).isEqualTo(0.0)
        assertThat(sut.isPumpStop.value).isFalse()
        assertThat(sut.runningRemainMinutes.value).isEqualTo(0)
        assertThat(sut.tempBasalRate.value).isNull()
        assertThat(sut.basalRate.value).isEqualTo(0.0)
    }

    @Test
    fun `observePatchState ignores an empty state report`() {
        sut.observePatchState()

        patchStateSubject.onNext(Optional.empty())

        assertThat(sut.patchState.value).isEqualTo(PatchState.NotConnectedNotBooting)
        assertThat(sut.basalRate.value).isNull()
    }

    @Test
    fun `observePatchState survives an errored stream`() {
        sut.observePatchState()

        patchStateSubject.onError(RuntimeException("boom"))

        assertThat(sut.patchState.value).isEqualTo(PatchState.NotConnectedNotBooting)
    }

    // ---- observeProfile -----------------------------------------------------------------------

    @Test
    fun `observeProfile republishes the basal rate`() {
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(0.85)
        sut.observeProfile()

        profileSubject.onNext(Optional.of(profile))

        assertThat(sut.basalRate.value).isEqualTo(0.85)
    }

    @Test
    fun `observeProfile zeroes the basal rate when the profile is cleared`() {
        sut.observeProfile()

        profileSubject.onNext(Optional.empty())

        assertThat(sut.basalRate.value).isEqualTo(0.0)
    }

    // ---- observeInfusionInfo ------------------------------------------------------------------

    @Test
    fun `observeInfusionInfo publishes the running temp basal speed`() {
        sut.observeInfusionInfo()

        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasal(speed = 3.5))))

        assertThat(sut.tempBasalRate.value).isEqualTo(3.5)
    }

    @Test
    fun `observeInfusionInfo clears the temp basal speed when nothing is running`() {
        sut.observeInfusionInfo()
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasal(speed = 3.5))))

        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel()))

        assertThat(sut.tempBasalRate.value).isNull()
    }

    @Test
    fun `observeInfusionInfo re-latches the needle screen when the infusion record is gone`() {
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = true)))
        sut.observeInfusionInfo()

        infusionSubject.onNext(Optional.empty())

        assertThat(sut.isCheckScreen.value).isEqualTo(CarelevoScreenType.NEEDLE_INSERTION)
    }

    @Test
    fun `observeInfusionInfo ignores a missing infusion record when no patch is known`() {
        sut.observeInfusionInfo()

        infusionSubject.onNext(Optional.empty())

        assertThat(sut.isCheckScreen.value).isNull()
        assertThat(sut.tempBasalRate.value).isNull()
    }

    @Test
    fun `observeInfusionInfo does not latch the needle screen while the needle is not yet inserted`() {
        patchInfoSubject.onNext(Optional.of(patchInfo(checkNeedle = false)))
        sut.observeInfusionInfo()

        infusionSubject.onNext(Optional.empty())

        assertThat(sut.isCheckScreen.value).isNull()
    }

    // ---- clearInfusionInfo / refreshPatchInfusionInfo -----------------------------------------

    @Test
    fun `clearInfusionInfo refreshes the patch status through the queue on success`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)

        sut.clearInfusionInfo(CarelevoDeleteInfusionRequestModel(true, false, true))

        verify(deleteInfusionInfoUseCase).execute(CarelevoDeleteInfusionRequestModel(true, false, true))
        verifyBlocking(commandQueue) { readStatus(any()) }
    }

    @Test
    fun `clearInfusionInfo does not refresh when the delete stream errors`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(deleteInfusionInfoUseCase.execute(any())).thenReturn(Single.error(RuntimeException("db down")))

        sut.clearInfusionInfo(CarelevoDeleteInfusionRequestModel(true, true, true))

        verifyBlocking(commandQueue, never()) { readStatus(any()) }
    }

    @Test
    fun `refreshPatchInfusionInfo reads the status through the queue when bluetooth is on`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)

        sut.refreshPatchInfusionInfo()

        verifyBlocking(commandQueue) { readStatus(any()) }
    }

    @Test
    fun `refreshPatchInfusionInfo is a no-op while bluetooth is off`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        sut.refreshPatchInfusionInfo()

        verifyBlocking(commandQueue, never()) { readStatus(any()) }
    }

    // ---- clearExpiredInfusions (driven by the init second-tick) --------------------------------

    @Test
    fun `the second tick deletes every infusion whose window has elapsed`() {
        val past = DateTime.now().minusHours(2)
        infusionSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    tempBasalInfusionInfo = tempBasal(durationMin = 30, createdAt = past),
                    immeBolusInfusionInfo = immeBolus(durationSeconds = 60, createdAt = past),
                    extendBolusInfusionInfo = extendBolus(durationMin = 30, createdAt = past)
                )
            )
        )

        // The tick runs on a real Dispatchers.Default clock (~1 s), so wait on the invocation.
        // atLeastOnce: the ticker keeps firing, so a strict times(1) could race a second tick.
        val captor = argumentCaptor<CarelevoUseCaseRequest>()
        verify(deleteInfusionInfoUseCase, timeout(3_000).atLeastOnce()).execute(captor.capture())
        assertThat(captor.firstValue).isEqualTo(CarelevoDeleteInfusionRequestModel(true, true, true))
    }

    @Test
    fun `the second tick keeps infusions that are still running or have no end time`() {
        infusionSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    // No duration → open-ended, never expires.
                    tempBasalInfusionInfo = tempBasal(durationMin = null),
                    // Ends in the future.
                    extendBolusInfusionInfo = extendBolus(durationMin = 120, createdAt = DateTime.now())
                )
            )
        )

        verify(deleteInfusionInfoUseCase, after(1_500).never()).execute(any())
    }

    // ---- triggerEvent / generateEventType ------------------------------------------------------

    @Test
    fun `triggerEvent forwards a plain overview event untouched`() {
        sut.triggerEvent(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
        sut.triggerEvent(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)
        sut.triggerEvent(CarelevoOverviewEvent.DiscardFailed)
        sut.triggerEvent(CarelevoOverviewEvent.ResumePumpFailed)
        sut.triggerEvent(CarelevoOverviewEvent.StopPumpFailed)
        sut.triggerEvent(CarelevoOverviewEvent.StartConnectionFlow)
        sut.triggerEvent(CarelevoOverviewEvent.ShowPumpDiscardDialog)

        assertThat(events).containsExactly(
            CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled,
            CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected,
            CarelevoOverviewEvent.DiscardFailed,
            CarelevoOverviewEvent.ResumePumpFailed,
            CarelevoOverviewEvent.StopPumpFailed,
            CarelevoOverviewEvent.StartConnectionFlow,
            CarelevoOverviewEvent.ShowPumpDiscardDialog
        ).inOrder()
    }

    @Test
    fun `triggerEvent maps an unlisted overview event to NoAction`() {
        // ShowPumpResumeDialog is not listed in generateEventType → the `else` arm.
        sut.triggerEvent(CarelevoOverviewEvent.ShowPumpResumeDialog)

        assertThat(events).containsExactly(CarelevoOverviewEvent.NoAction)
    }

    @Test
    fun `triggerEvent ignores events belonging to another screen`() {
        sut.triggerEvent(CarelevoConnectEvent.ExitFlow)

        assertThat(events).isEmpty()
    }

    @Test
    fun `stop-resume click reports the patch is not connected when there is no patch`() {
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.NotConnectedNotBooting)

        sut.triggerEvent(CarelevoOverviewEvent.ClickPumpStopResumeBtn)

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)
    }

    @Test
    fun `stop-resume click offers the resume dialog for a stopped pump`() {
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.ConnectedBooted)
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = true)))

        sut.triggerEvent(CarelevoOverviewEvent.ClickPumpStopResumeBtn)

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowPumpResumeDialog)
    }

    @Test
    fun `stop-resume click offers the stop-duration dialog for a running pump`() {
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.ConnectedBooted)
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = false)))

        sut.triggerEvent(CarelevoOverviewEvent.ClickPumpStopResumeBtn)

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
    }

    @Test
    fun `stop-resume click assumes running when the stop flag is unknown`() {
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.NotConnectedBooted)
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = null)))

        sut.triggerEvent(CarelevoOverviewEvent.ClickPumpStopResumeBtn)

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
    }

    @Test
    fun `stop-resume click assumes running when no patch report has arrived`() {
        // patchInfo never emitted → `.value` is null → the `?: false` default.
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.ConnectedBooted)

        sut.triggerEvent(CarelevoOverviewEvent.ClickPumpStopResumeBtn)

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
    }

    // ---- startDiscardProcess -------------------------------------------------------------------

    @Test
    fun `startDiscardProcess does nothing when no patch state is known`() {
        sut.startDiscardProcess()

        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        verify(patchForceDiscardUseCase, never()).execute()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startDiscardProcess does nothing when there is no active patch`() {
        patchStateSubject.onNext(Optional.of(PatchState.NotConnectedNotBooting))

        sut.startDiscardProcess()

        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        verify(patchForceDiscardUseCase, never()).execute()
    }

    @Test
    fun `startDiscardProcess routes an active patch through the queue`() {
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        stubCustomCommand(success = true)

        sut.startDiscardProcess()

        verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
        // The queue's CmdDiscard owns unBond + releasePatch; the VM must not double-tear-down.
        verify(carelevoPatch, never()).discardTeardown()
        verify(patchForceDiscardUseCase, never()).execute()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(events).isEmpty()
    }

    @Test
    fun `startDiscardProcess falls back to force-discard when the queue cannot reach the patch`() {
        patchStateSubject.onNext(Optional.of(PatchState.NotConnectedBooted))
        stubCustomCommand(success = false)
        whenever(patchForceDiscardUseCase.execute())
            .thenReturn(Single.just(ResponseResult.Success(ResultSuccess as CarelevoUseCaseResponse)))

        sut.startDiscardProcess()

        verify(patchForceDiscardUseCase).execute()
        verify(carelevoPatch).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(events).isEmpty()
    }

    @Test
    fun `startDiscardProcess reports failure when force-discard is rejected`() {
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        stubCustomCommand(success = false)
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(ResponseResult.Failure("rejected")))

        sut.startDiscardProcess()

        assertThat(events).containsExactly(CarelevoOverviewEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startDiscardProcess reports failure when force-discard returns an error result`() {
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        stubCustomCommand(success = false)
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(ResponseResult.Error(RuntimeException("db"))))

        sut.startDiscardProcess()

        assertThat(events).containsExactly(CarelevoOverviewEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startDiscardProcess reports failure when the force-discard stream throws`() {
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        stubCustomCommand(success = false)
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.error(RuntimeException("boom")))

        sut.startDiscardProcess()

        assertThat(events).containsExactly(CarelevoOverviewEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- startPumpStopProcess ------------------------------------------------------------------

    @Test
    fun `startPumpStopProcess reports bluetooth off and never touches the queue`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        sut.startPumpStopProcess(30)

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPumpStopProcess stops an idle pump and syncs the suspension`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        patchInfoSubject.onNext(Optional.of(patchInfo(manufactureNumber = "SN-0001")))
        stubCustomCommand(success = true)

        sut.startPumpStopProcess(45)

        val captor = argumentCaptor<CustomCommand>()
        verifyBlocking(commandQueue) { customCommand(captor.capture()) }
        assertThat((captor.firstValue as CmdPumpStop).durationMin).isEqualTo(45)
        // Nothing was running → no pre-cancel round-trips.
        verifyBlocking(commandQueue, never()) { cancelTempBasal(any(), any()) }
        verifyBlocking(commandQueue, never()) { cancelExtended() }
        verifyBlocking(pumpSync) {
            syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())
        }
        verifyBlocking(pumpSync) { syncStopExtendedBolusWithPumpId(any(), any(), any(), any()) }
        verify(deleteInfusionInfoUseCase).execute(CarelevoDeleteInfusionRequestModel(false, false, false))
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(events).isEmpty()
    }

    @Test
    fun `startPumpStopProcess cancels a running temp basal before stopping`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasal())))
        stubCancelTempBasal(success = true)
        stubCustomCommand(success = true)

        sut.startPumpStopProcess(60)

        verifyBlocking(commandQueue) { cancelTempBasal(any(), any()) }
        verifyBlocking(commandQueue, never()) { cancelExtended() }
        verifyBlocking(commandQueue) { customCommand(any<CmdPumpStop>()) }
        // The cleanup must mirror what was actually running.
        verify(deleteInfusionInfoUseCase).execute(CarelevoDeleteInfusionRequestModel(true, false, false))
        assertThat(events).isEmpty()
    }

    @Test
    fun `startPumpStopProcess cancels a running extended bolus before stopping`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(extendBolusInfusionInfo = extendBolus())))
        stubCancelExtended(success = true)
        stubCustomCommand(success = true)

        sut.startPumpStopProcess(60)

        verifyBlocking(commandQueue) { cancelExtended() }
        verifyBlocking(commandQueue, never()) { cancelTempBasal(any(), any()) }
        verifyBlocking(commandQueue) { customCommand(any<CmdPumpStop>()) }
        verify(deleteInfusionInfoUseCase).execute(CarelevoDeleteInfusionRequestModel(false, false, true))
        assertThat(events).isEmpty()
    }

    @Test
    fun `startPumpStopProcess cancels both running infusions before stopping`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        infusionSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    tempBasalInfusionInfo = tempBasal(),
                    extendBolusInfusionInfo = extendBolus()
                )
            )
        )
        stubCancelTempBasal(success = true)
        stubCancelExtended(success = true)
        stubCustomCommand(success = true)

        sut.startPumpStopProcess(15)

        verifyBlocking(commandQueue) { cancelTempBasal(any(), any()) }
        verifyBlocking(commandQueue) { cancelExtended() }
        verify(deleteInfusionInfoUseCase).execute(CarelevoDeleteInfusionRequestModel(true, false, true))
        assertThat(events).isEmpty()
    }

    @Test
    fun `startPumpStopProcess aborts when the temp basal cannot be cancelled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasal())))
        stubCancelTempBasal(success = false)

        sut.startPumpStopProcess(30)

        assertThat(events).containsExactly(CarelevoOverviewEvent.StopPumpFailed)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPumpStopProcess aborts when the extended bolus cannot be cancelled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(extendBolusInfusionInfo = extendBolus())))
        stubCancelExtended(success = false)

        sut.startPumpStopProcess(30)

        assertThat(events).containsExactly(CarelevoOverviewEvent.StopPumpFailed)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPumpStopProcess reports failure when the queued stop frame fails`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(success = false)

        sut.startPumpStopProcess(30)

        assertThat(events).containsExactly(CarelevoOverviewEvent.StopPumpFailed)
        // A failed stop must not fabricate a suspension in the DB.
        verifyBlocking(pumpSync, never()) {
            syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())
        }
        verify(deleteInfusionInfoUseCase, never()).execute(any())
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPumpStopProcess syncs an empty serial when no patch report has arrived`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(success = true)

        sut.startPumpStopProcess(30)

        val serial = argumentCaptor<String>()
        verifyBlocking(pumpSync) { syncStopExtendedBolusWithPumpId(any(), any(), any(), serial.capture()) }
        assertThat(serial.firstValue).isEqualTo("")
    }

    // ---- startPumpResume -----------------------------------------------------------------------

    @Test
    fun `startPumpResume reports bluetooth off and never touches the queue`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        sut.startPumpResume()

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPumpResume ends the suspension TBR when the queued frame succeeds`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        patchInfoSubject.onNext(Optional.of(patchInfo(manufactureNumber = "SN-0001", isStopped = true)))
        stubCustomCommand(success = true)

        sut.startPumpResume()

        verifyBlocking(commandQueue) { customCommand(any<CmdPumpResume>()) }
        val serial = argumentCaptor<String>()
        // `ignorePumpIds` has a default value → the mock records all 5 args.
        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), serial.capture(), any()) }
        assertThat(serial.firstValue).isEqualTo("SN-0001")
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(events).isEmpty()
    }

    @Test
    fun `startPumpResume reports failure and leaves the TBR alone when the queued frame fails`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        stubCustomCommand(success = false)

        sut.startPumpResume()

        assertThat(events).containsExactly(CarelevoOverviewEvent.ResumePumpFailed)
        verifyBlocking(pumpSync, never()) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- overviewUiState assembly ---------------------------------------------------------------

    @Test
    fun `overview warns and offers connect while no patch is activated`() {
        val state = sut.overviewUiState.value

        assertThat(state.statusBanner).isNotNull()
        assertThat(state.statusBanner?.text).isEqualTo(s(R.string.carelevo_state_none_value))
        assertThat(state.statusBanner?.level).isEqualTo(StatusLevel.WARNING)
        assertThat(state.queueStatus).isNull()
        // Only the bluetooth-state row; no patch data to show.
        assertThat(infoRows()).hasSize(1)
        assertThat(infoRows()[0].label).isEqualTo(s(R.string.carelevo_bluetooth_state_key))
        assertThat(infoRows()[0].value).isEqualTo(s(R.string.carelevo_state_none_value))
        assertThat(state.primaryActions).hasSize(1)
        assertThat(state.primaryActions[0].label).isEqualTo(s(R.string.carelevo_overview_connect_btn_label))
        assertThat(state.primaryActions[0].category).isEqualTo(ActionCategory.PRIMARY)
        assertThat(state.managementActions).isEmpty()
    }

    @Test
    fun `overview shows the full status for a connected patch`() {
        val profile = mock<Profile>()
        whenever(profile.getBasal()).thenReturn(1.75)
        profileSubject.onNext(Optional.of(profile))
        sut.observePatchInfo()
        sut.observePatchState()
        sut.observeInfusionInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo()))
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasal(speed = 2.5))))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        connectedFlow.value = true
        val state = sut.overviewUiState.value
        val rows = infoRows()

        // An activated patch surfaces the shared communication status instead of a warning banner.
        assertThat(state.statusBanner).isNull()
        assertThat(rows).hasSize(11)
        assertThat(rows[0].value).isEqualTo(s(R.string.carelevo_state_connected_value))
        assertThat(rows[1].label).isEqualTo(s(CoreUiR.string.last_connection_label))
        assertThat(rows[2].label).isEqualTo(s(R.string.carelevo_serial_number_key))
        assertThat(rows[2].value).isEqualTo("SN-0001")
        assertThat(rows[3].value).isEqualTo("T168")
        assertThat(rows[4].value).isEqualTo(uiFormat(local(nowMillis - 2 * dayMillis)))
        assertThat(rows[5].value).isEqualTo(uiFormat(local(nowMillis - 2 * dayMillis).plusDays(7)))
        assertThat(rows[7].value).isEqualTo(s(R.string.common_label_unit_value_dose_per_speed_with_space, 1.75))
        assertThat(rows[8].value).isEqualTo(s(R.string.common_label_unit_value_dose_per_speed_with_space, 2.5))
        assertThat(rows[10].value).isEqualTo(
            s(R.string.common_label_unit_value_dose_with_space, String.format(Locale.US, "%.2f", 3.58))
        )
        assertThat(state.primaryActions).isEmpty()
        assertThat(state.managementActions).hasSize(2)
        assertThat(state.managementActions[0].label).isEqualTo(s(R.string.carelevo_overview_pump_discard_btn_label))
        assertThat(state.managementActions[0].category).isEqualTo(ActionCategory.MANAGEMENT)
        assertThat(state.managementActions[1].label).isEqualTo(s(CoreUiR.string.pump_suspend))
    }

    @Test
    fun `overview keeps the last known status for an idle-disconnected patch`() {
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo()))
        patchStateSubject.onNext(Optional.of(PatchState.NotConnectedBooted))

        val state = sut.overviewUiState.value

        // Idle-disconnect is normal (the queue reconnects on demand) → no warning banner.
        assertThat(state.statusBanner).isNull()
        assertThat(infoRows()).hasSize(11)
        assertThat(infoRows()[0].value).isEqualTo(s(R.string.carelevo_state_disconnected_value))
        assertThat(state.primaryActions).isEmpty()
        assertThat(state.managementActions).hasSize(2)
    }

    @Test
    fun `overview offers resume instead of suspend for a stopped pump`() {
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = true)))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        assertThat(sut.overviewUiState.value.managementActions[1].label).isEqualTo(s(CoreUiR.string.pump_resume))
    }

    @Test
    fun `a stopped pump shows the suspended banner as the top status`() {
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = true)))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        val banner = sut.overviewUiState.value.statusBanner
        assertThat(banner?.text).isEqualTo(s(CoreUiR.string.pumpsuspended))
        assertThat(banner?.level).isEqualTo(StatusLevel.WARNING)
    }

    @Test
    fun `the bluetooth row goes live and the last-connection row shows time since the last session`() {
        whenever(dateUtil.minAgo(any(), any())).thenReturn("5m ago")
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo()))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        lastConnectedFlow.value = nowMillis - 5 * 60_000L

        // Idle: Bluetooth row reads Disconnected even though the patch is activated.
        assertThat(infoRows()[0].value).isEqualTo(s(R.string.carelevo_state_disconnected_value))
        // Row 1 is the "last connection: N ago" reachability line (shared DateUtil.minAgo).
        val lastConnection = infoRows()[1]
        assertThat(lastConnection.label).isEqualTo(s(CoreUiR.string.last_connection_label))
        assertThat(lastConnection.value).isEqualTo("5m ago")

        // A live session flips it to Connected.
        connectedFlow.value = true
        assertThat(infoRows()[0].value).isEqualTo(s(R.string.carelevo_state_connected_value))
    }

    @Test
    fun `overview dashes out unknown patch values`() {
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(
            Optional.of(
                patchInfo(
                    manufactureNumber = null, firmwareVersion = null, insulinRemain = null,
                    bootDateTimeUtcMillis = null, bootDateTime = null
                )
            )
        )
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        val rows = infoRows()
        assertThat(rows[2].value).isEqualTo("-")
        assertThat(rows[3].value).isEqualTo("-")
        assertThat(rows[4].value).isEqualTo("-")
        assertThat(rows[5].value).isEqualTo("-")
        // runningRemainMinutes == 0 → nothing to count down.
        assertThat(rows[6].value).isEqualTo("-")
        assertThat(rows[9].value).isEqualTo("-")
    }

    @Test
    fun `overview formats a multi-day remaining time with the day-hour-minute template`() {
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo(bootDateTimeUtcMillis = nowMillis - 2 * dayMillis)))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        val total = sut.runningRemainMinutes.value!!
        val expected = s(R.string.common_unit_value_day_hour_min, total / 1440, (total % 1440) / 60, total % 60)
        assertThat(infoRows()[6].value).isEqualTo(expected)
        assertThat(total / 1440).isGreaterThan(0)
    }

    @Test
    fun `overview formats a sub-day remaining time as hours and minutes`() {
        // Booted 7 days ago plus 100 minutes → ~100 min left, i.e. the days == 0 branch.
        val bootMillis = nowMillis - 7 * dayMillis + 100 * 60_000L
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo(bootDateTimeUtcMillis = bootMillis)))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        // Derived, not hardcoded: the VM measures the countdown in local-calendar minutes.
        val expected = ChronoUnit.MINUTES.between(local(nowMillis), local(bootMillis).plusDays(7)).toInt()
        val total = sut.runningRemainMinutes.value!!
        assertThat(total).isEqualTo(expected)
        assertThat(total).isGreaterThan(0)
        assertThat(total).isLessThan(1440)
        assertThat(infoRows()[6].value)
            .isEqualTo(String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60))
    }

    @Test
    fun `overview temp basal row falls back to zero when nothing is running`() {
        sut.observePatchInfo()
        sut.observePatchState()
        sut.observeInfusionInfo()
        patchInfoSubject.onNext(Optional.of(patchInfo()))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        infusionSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel()))

        assertThat(infoRows()[8].value).isEqualTo(s(R.string.common_label_unit_value_dose_per_speed_with_space, 0.0))
    }

    @Test
    fun `overview drops back to the warning banner once the patch is discarded`() {
        sut.observePatchState()
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        assertThat(sut.overviewUiState.value.statusBanner).isNull()

        patchStateSubject.onNext(Optional.of(PatchState.NotConnectedNotBooting))

        val state = sut.overviewUiState.value
        assertThat(state.statusBanner?.level).isEqualTo(StatusLevel.WARNING)
        assertThat(infoRows()).hasSize(1)
        assertThat(state.managementActions).isEmpty()
        assertThat(state.primaryActions).hasSize(1)
    }

    // ---- overview action wiring -----------------------------------------------------------------

    @Test
    fun `the connect action asks the screen to start the connection flow`() {
        sut.overviewUiState.value.primaryActions[0].onClick()

        assertThat(events).containsExactly(CarelevoOverviewEvent.StartConnectionFlow)
    }

    @Test
    fun `the discard action asks the screen for confirmation`() {
        sut.observePatchState()
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        sut.overviewUiState.value.managementActions[0].onClick()

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowPumpDiscardDialog)
    }

    @Test
    fun `the suspend action resolves to the stop-duration dialog`() {
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = false)))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.ConnectedBooted)

        sut.overviewUiState.value.managementActions[1].onClick()

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
    }

    @Test
    fun `the resume action resolves to the resume dialog`() {
        sut.observePatchInfo()
        sut.observePatchState()
        patchInfoSubject.onNext(Optional.of(patchInfo(isStopped = true)))
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.ConnectedBooted)

        sut.overviewUiState.value.managementActions[1].onClick()

        assertThat(events).containsExactly(CarelevoOverviewEvent.ShowPumpResumeDialog)
    }
}
