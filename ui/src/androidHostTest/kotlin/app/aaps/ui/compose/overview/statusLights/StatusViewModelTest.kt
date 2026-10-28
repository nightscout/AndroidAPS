package app.aaps.ui.compose.overview.statusLights

import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.interfaces.source.CgmSensorStatusProvider
import app.aaps.core.interfaces.source.CgmStagingEvidence
import app.aaps.core.interfaces.source.CgmWarmupProvider
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TimeDiff
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.StatusLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
internal class StatusViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var config: Config
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var tddCalculator: TddCalculator
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock private lateinit var aapsLogger: AAPSLogger

    private val teChanges = MutableSharedFlow<List<TE>>()
    private val reservoir = MutableStateFlow(PumpInsulin(10.0))

    /** Refreshes that have started: each one reads the cannula change once. */
    private val started = AtomicInteger(0)

    /** Refreshes that have written the cannula usage, which is the last thing a refresh does. */
    private val finished = AtomicInteger(0)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(rxBus.toFlow(EventInitializationChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventConfigBuilderChange::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventNsClientStatusUpdated::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(teChanges)
        whenever(persistenceLayer.databaseClearedFlow).thenReturn(emptyFlow())
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(textResolver: TextResolver = rh) = StatusViewModel(
        textResolver, activePlugin, profileFunction, config, persistenceLayer, dateUtil, rxBus, preferences,
        tddCalculator, decimalFormatter, processedDeviceStatusData, aapsLogger
    )

    @Test
    fun `default uiState has no status items and hidden actions`() {
        val state = newViewModel().uiState.value
        assertThat(state.sensorStatus).isNull()
        assertThat(state.insulinStatus).isNull()
        assertThat(state.showFill).isFalse()
        assertThat(state.showPumpBatteryChange).isFalse()
        assertThat(state.isPatchPump).isFalse()
    }

    /**
     * Every screen collects the state with collectAsStateWithLifecycle, so with the app in the background
     * nobody collects. Then nothing may run: no database reads, no cannula usage, no minute ticker (#5246).
     */
    @Test
    fun `nothing is refreshed while no screen shows the status lights`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        givenStatusData()
        newViewModel()

        teChanges.emit(emptyList())
        reservoir.value = PumpInsulin(20.0)
        delay(QUIET_MS)

        assertThat(started.get()).isEqualTo(0)
    }

    @Test
    fun `a screen that starts showing the status lights gets one refresh`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        givenStatusData()
        val viewModel = newViewModel()

        val screen = show(viewModel)
        awaitValue(finished, 1)
        delay(QUIET_MS)

        assertThat(started.get()).isEqualTo(1)
        assertThat(viewModel.uiState.value.cannulaStatus?.level).isEqualTo(USAGE_TEXT)
        screen.cancelAndJoin()
    }

    /**
     * Reservoir and battery are the only pump values the status lights show. A new value refreshes, the
     * same value does not. Pump status events are not used any more: they came many times per
     * connection with nothing changed.
     */
    @Test
    fun `a new reservoir level refreshes, the same level does not`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        givenStatusData()
        val viewModel = newViewModel()
        val screen = show(viewModel)
        awaitValue(finished, 1)

        reservoir.value = PumpInsulin(10.0)
        delay(QUIET_MS)
        assertThat(started.get()).isEqualTo(1)

        reservoir.value = PumpInsulin(9.5)
        assertThat(awaitValue(finished, 2)).isEqualTo(2)
        screen.cancelAndJoin()
    }

    /**
     * Triggers can come close together. Each refresh used to start its own cannula usage calculation and
     * none was cancelled, so they piled up on the database. Only one may run at a time: a newer refresh
     * cancels the older one.
     */
    @Test
    fun `a new refresh cancels the cannula usage calculation still running`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        givenStatusData()
        val running = AtomicInteger(0)
        val tddDone = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        whenever(tddCalculator.calculateIntervalWithCachedDays(any(), any(), any())).doSuspendableAnswer {
            running.incrementAndGet()
            try {
                gate.await()
            } finally {
                running.decrementAndGet()
            }
            tddDone.incrementAndGet()
            TDD(timestamp = 0, totalAmount = 12.0)
        }
        val viewModel = newViewModel()
        val screen = show(viewModel)
        assertThat(awaitValue(running, 1)).isEqualTo(1)

        // Three therapy event changes while the calculation is still running. Each must end the one before.
        repeat(3) {
            val before = started.get()
            teChanges.emit(emptyList())
            awaitMore(started, before)
            assertThat(awaitValue(running, 1)).isEqualTo(1)
        }

        gate.complete(Unit)
        assertThat(awaitValue(running, 0)).isEqualTo(0)
        assertThat(tddDone.get()).isEqualTo(1)

        // Wait until the last refresh is back on Main and has written the usage. `running` drops inside
        // the IO hop, so without this the test could return while that coroutine is still on its way
        // back to Main. tearDown() then resets Main, the resume crashes, and the next runTest in this
        // JVM fails with UncaughtExceptionsBeforeTest (seen on CI in an unrelated test class).
        awaitValue(finished, 1)
        screen.cancelAndJoin()
    }

    // ---- CGM warm-up and second sensor rows ----

    @Test
    fun `a source that reports neither warm-up nor a second sensor gets no extra row`() {
        whenever(activePlugin.activeBgSource).thenReturn(PlainBgSource())
        val viewModel = newViewModel(NameText)

        assertThat(viewModel.buildWarmUpStatus()).isNull()
        assertThat(viewModel.buildSecondSensorStatus()).isNull()
        assertThat(viewModel.canPromoteSecondSensor()).isFalse()
        viewModel.refreshCgmRows()
        assertThat(viewModel.uiState.value).isEqualTo(StatusUiState())
    }

    @Test
    fun `warm-up row counts down from the wall-clock end time, not from the stored countdown`() {
        val source = givenCgmSource()
        source.warmupStatus.value = warming(
            // Stale on purpose: it was stored at the last radio window. The end time must win.
            remainingMs = 5 * MINUTE_MS,
            endsAtEpochMs = NOW + 23 * MINUTE_MS,
            totalMs = 30 * MINUTE_MS
        )

        val item = newViewModel(NameText).buildWarmUpStatus()

        assertThat(item).isNotNull()
        assertThat(item!!.label).isEqualTo("overview_cgm_warmup_label")
        assertThat(item.age).isEqualTo("format_mins(23)")
        // 7 of the 30 minutes are done.
        assertThat(item.agePercent).isWithin(0.001f).of(7f / 30f)
    }

    @Test
    fun `warm-up row uses the stored countdown when there is no end time`() {
        val source = givenCgmSource()
        source.warmupStatus.value = warming(remainingMs = 12 * MINUTE_MS, endsAtEpochMs = null, totalMs = null)

        val item = newViewModel(NameText).buildWarmUpStatus()

        assertThat(item!!.age).isEqualTo("format_mins(12)")
        // No total length known: no bar.
        assertThat(item.agePercent).isEqualTo(-1f)
    }

    @Test
    fun `warm-up minutes are rounded up and never read 0 while warming`() {
        val source = givenCgmSource()
        val viewModel = newViewModel(NameText)

        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = NOW + 22 * MINUTE_MS + 1_000L)
        assertThat(viewModel.buildWarmUpStatus()!!.age).isEqualTo("format_mins(23)")

        // The end time has passed but the source still says warming: no "0 min", it waits.
        source.warmupStatus.value = warming(remainingMs = 3 * MINUTE_MS, endsAtEpochMs = NOW - 1_000L)
        assertThat(viewModel.buildWarmUpStatus()!!.age).isEqualTo("overview_cgm_warmup_waiting")

        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = null)
        assertThat(viewModel.buildWarmUpStatus()!!.age).isEqualTo("overview_cgm_warmup_waiting")
    }

    @Test
    fun `a reconnect without a warm-up end time ahead is not shown as a warm-up`() {
        val source = givenCgmSource()
        val viewModel = newViewModel(NameText)

        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = null).copy(phase = CgmWarmupStatus.Phase.RECONNECTING)
        assertThat(viewModel.buildWarmUpStatus()).isNull()

        // A reconnect during the warm-up keeps the warm-up deadline, and then the row stays.
        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = NOW + 10 * MINUTE_MS)
            .copy(phase = CgmWarmupStatus.Phase.RECONNECTING)
        assertThat(viewModel.buildWarmUpStatus()!!.age).isEqualTo("format_mins(10)")
    }

    @Test
    fun `warm-up row disappears once the warm-up is over`() {
        val source = givenCgmSource()
        val viewModel = newViewModel(NameText)
        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = NOW + MINUTE_MS)
        viewModel.refreshCgmRows()
        assertThat(viewModel.uiState.value.warmUpStatus).isNotNull()

        // The source reports nothing any more once the sensor is ready.
        source.warmupStatus.value = null
        viewModel.refreshCgmRows()
        assertThat(viewModel.uiState.value.warmUpStatus).isNull()

        // An inactive status is the same as none.
        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = NOW + MINUTE_MS).copy(active = false)
        viewModel.refreshCgmRows()
        assertThat(viewModel.uiState.value.warmUpStatus).isNull()
    }

    @Test
    fun `second sensor row is absent while no second sensor is staged`() {
        givenCgmSource()

        assertThat(newViewModel(NameText).buildSecondSensorStatus()).isNull()
    }

    @Test
    fun `second sensor in warm-up says so and shows its readings`() {
        val source = givenCgmSource()
        source.stagingState.value = StagingState.WARMUP
        source.stagingEvidence.value = CgmStagingEvidence(validCount = 0, lastValueMgdl = null, lastValueAtEpochMs = null)

        val item = newViewModel(NameText).buildSecondSensorStatus()!!

        assertThat(item.label).isEqualTo("overview_second_sensor_label")
        assertThat(item.age).isEqualTo("overview_second_sensor_warmup")
        assertThat(item.level).isEqualTo("overview_second_sensor_readings(0)")
        assertThat(item.agePercent).isEqualTo(-1f)
    }

    @Test
    fun `settling second sensor shows whole hours left, rounded up, from the time the source publishes`() {
        val source = givenCgmSource()
        val viewModel = newViewModel(NameText)
        source.stagingState.value = StagingState.SETTLING
        source.stagingLifecycle.value = stagingLifecycle(ageMs = 4 * HOUR_MS)
        source.stagingEvidence.value = CgmStagingEvidence(validCount = 12, lastValueMgdl = 118.0, lastValueAtEpochMs = NOW)

        // 8 h 5 min left reads 9 h: the wait is not over until it is over.
        source.stagingSettleRemainingMs.value = 8 * HOUR_MS + 5 * MINUTE_MS
        val item = viewModel.buildSecondSensorStatus()!!
        assertThat(item.age).isEqualTo("overview_second_sensor_ready_in(units_format_hours(9))")
        assertThat(item.level).isEqualTo("overview_second_sensor_readings(12)")
        assertThat(item.agePercent).isWithin(0.001f).of(4f / (4f + 8f + 5f / 60f))

        source.stagingSettleRemainingMs.value = 8 * HOUR_MS
        assertThat(viewModel.buildSecondSensorStatus()!!.age).isEqualTo("overview_second_sensor_ready_in(units_format_hours(8))")

        // One minute left still reads 1 h, never 0 h.
        source.stagingSettleRemainingMs.value = MINUTE_MS
        assertThat(viewModel.buildSecondSensorStatus()!!.age).isEqualTo("overview_second_sensor_ready_in(units_format_hours(1))")

        // A different rule in the source gives a different time here: this screen has no copy of it.
        source.stagingSettleRemainingMs.value = 20 * HOUR_MS
        assertThat(viewModel.buildSecondSensorStatus()!!.age).isEqualTo("overview_second_sensor_ready_in(units_format_hours(20))")
    }

    @Test
    fun `settling second sensor with no time left waits for readings and does not say ready`() {
        val source = givenCgmSource()
        source.stagingState.value = StagingState.SETTLING
        source.stagingSettleRemainingMs.value = null

        val viewModel = newViewModel(NameText)

        assertThat(viewModel.buildSecondSensorStatus()!!.age).isEqualTo("overview_second_sensor_waiting_readings")
        assertThat(viewModel.canPromoteSecondSensor()).isFalse()
    }

    @Test
    fun `ready second sensor is marked normal, drops the countdown and offers the switch`() {
        val source = givenCgmSource()
        source.stagingState.value = StagingState.READY
        source.stagingLifecycle.value = stagingLifecycle(ageMs = 13 * HOUR_MS)
        source.stagingEvidence.value = CgmStagingEvidence(validCount = 72, lastValueMgdl = 110.0, lastValueAtEpochMs = NOW)
        val viewModel = newViewModel(NameText)

        viewModel.refreshCgmRows()

        val state = viewModel.uiState.value
        assertThat(state.secondSensorStatus!!.age).isEqualTo("overview_second_sensor_ready")
        assertThat(state.secondSensorStatus.ageStatus).isEqualTo(StatusLevel.NORMAL)
        assertThat(state.secondSensorStatus.agePercent).isEqualTo(-1f)
        assertThat(state.secondSensorStatus.level).isEqualTo("overview_second_sensor_readings(72)")
        assertThat(state.canPromoteSecondSensor).isTrue()
    }

    @Test
    fun `the switch is offered only once the second sensor is ready`() {
        val source = givenCgmSource()
        val viewModel = newViewModel(NameText)

        for (state in StagingState.entries) {
            source.stagingState.value = state
            viewModel.refreshCgmRows()
            assertThat(viewModel.canPromoteSecondSensor()).isEqualTo(state == StagingState.READY)
            assertThat(viewModel.uiState.value.canPromoteSecondSensor).isEqualTo(state == StagingState.READY)
        }
    }

    @Test
    fun `a source with only a warm-up never offers the switch`() {
        val source = WarmupOnlyBgSource()
        whenever(activePlugin.activeBgSource).thenReturn(source)
        whenever(dateUtil.now()).thenReturn(NOW)
        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = NOW + MINUTE_MS)
        val viewModel = newViewModel(NameText)

        viewModel.refreshCgmRows()

        assertThat(viewModel.uiState.value.warmUpStatus).isNotNull()
        assertThat(viewModel.uiState.value.secondSensorStatus).isNull()
        assertThat(viewModel.uiState.value.canPromoteSecondSensor).isFalse()
    }

    @Test
    fun `every switch result has its own wording`() {
        val viewModel = newViewModel(NameText)
        val results = listOf(
            PromotionResult.Ok,
            PromotionResult.OkBoundFailed,
            PromotionResult.OkFollowUpFailed(productionIdentityPresent = true),
            PromotionResult.OkFollowUpFailed(productionIdentityPresent = false)
        ) + PromotionRejectReason.entries.map { PromotionResult.Rejected(it) }

        val messages = results.map { viewModel.promotionMessage(it) }

        // One text per result: a user who is refused, or whose switch only half worked, must know why.
        assertThat(messages).containsNoDuplicates()
        assertThat(messages).hasSize(8)
        assertThat(viewModel.promotionMessage(PromotionResult.Ok)).isEqualTo("overview_second_sensor_switch_ok")
        assertThat(viewModel.promotionMessage(PromotionResult.OkBoundFailed))
            .isEqualTo("overview_second_sensor_switch_ok_calibration_failed")
    }

    @Test
    fun `the switch asks the source for a normal switch, never an early one, and shows the result`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val source = givenCgmSource()
        source.stagingState.value = StagingState.READY
        source.result = PromotionResult.Rejected(PromotionRejectReason.STAGING_NO_RECENT_GLUCOSE)
        val viewModel = newViewModel(NameText)

        viewModel.promoteSecondSensor()
        val message = withTimeoutOrNull(20_000) {
            while (viewModel.uiState.value.promotionMessage == null) delay(10)
            viewModel.uiState.value.promotionMessage
        }

        assertThat(message).isEqualTo("overview_second_sensor_switch_refused_no_recent_reading")
        assertThat(source.calls).containsExactly(false)
        viewModel.clearPromotionMessage()
        assertThat(viewModel.uiState.value.promotionMessage).isNull()
    }

    @Test
    fun `a switch that throws tells the user to check which sensor is in use`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val source = givenCgmSource()
        source.stagingState.value = StagingState.READY
        source.failure = IllegalStateException("boom")
        val viewModel = newViewModel(NameText)

        viewModel.promoteSecondSensor()
        val message = withTimeoutOrNull(20_000) {
            while (viewModel.uiState.value.promotionMessage == null) delay(10)
            viewModel.uiState.value.promotionMessage
        }

        assertThat(message).isEqualTo("overview_second_sensor_switch_error")
    }

    /**
     * The 10 s tick runs while the card is shown. For a source with no warm-up and no second sensor
     * it must not change anything. Virtual time, and a bounded window: the ticker re-arms itself,
     * so it never goes idle.
     */
    @Test
    fun `the cgm tick leaves the state alone for a source with neither row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reads = AtomicInteger(0)
        val plain = PlainBgSource()
        whenever(activePlugin.activeBgSource).thenAnswer { reads.incrementAndGet(); plain }
        givenFullRefreshNeverEnds()
        val viewModel = newViewModel(NameText)
        val emitted = mutableListOf<StatusUiState>()
        backgroundScope.launch { viewModel.uiState.collect { emitted += it } }
        runCurrent()
        val before = viewModel.uiState.value
        val readsBefore = reads.get()

        // Four more ticks (10, 20, 30, 40 s), and still before the 60 s full refresh.
        advanceTimeBy(4 * CGM_TICK_MS)
        runCurrent()

        assertThat(reads.get() - readsBefore).isAtLeast(4)
        assertThat(viewModel.uiState.value).isSameInstanceAs(before)
        assertThat(emitted).containsExactly(StatusUiState())
    }

    @Test
    fun `a warm-up row goes away on the next tick once the source stops reporting it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = givenCgmSource()
        givenFullRefreshNeverEnds()
        source.warmupStatus.value = warming(remainingMs = null, endsAtEpochMs = NOW + 10 * MINUTE_MS)
        val viewModel = newViewModel(NameText)
        backgroundScope.launch { viewModel.uiState.collect { } }
        runCurrent()
        assertThat(viewModel.uiState.value.warmUpStatus).isNotNull()

        source.warmupStatus.value = null
        advanceTimeBy(CGM_TICK_MS)
        runCurrent()

        assertThat(viewModel.uiState.value.warmUpStatus).isNull()
    }

    @Test
    fun `a second tap while the switch is running does not start another switch`() = runBlocking<Unit> {
        // Not Unconfined: the first switch must really be waiting when the second tap comes.
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val source = givenCgmSource()
        source.stagingState.value = StagingState.READY
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val viewModel = newViewModel(NameText)

        viewModel.promoteSecondSensor()
        scheduler.runCurrent()
        withTimeout(20_000) { while (synchronized(source.calls) { source.calls.size } < 1) delay(10) }
        viewModel.promoteSecondSensor()
        scheduler.runCurrent()
        gate.complete(Unit)
        val message = withTimeoutOrNull(20_000) {
            while (viewModel.uiState.value.promotionMessage == null) {
                scheduler.runCurrent()
                delay(10)
            }
            viewModel.uiState.value.promotionMessage
        }

        assertThat(message).isEqualTo("overview_second_sensor_switch_ok")
        assertThat(source.calls).containsExactly(false)
    }

    @Test
    fun `after a successful switch the second sensor row and its button are gone`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val source = givenCgmSource()
        source.stagingState.value = StagingState.READY
        source.stagingEvidence.value = CgmStagingEvidence(validCount = 72, lastValueMgdl = 110.0, lastValueAtEpochMs = NOW)
        val viewModel = newViewModel(NameText)
        viewModel.refreshCgmRows()
        assertThat(viewModel.uiState.value.secondSensorStatus).isNotNull()
        assertThat(viewModel.uiState.value.canPromoteSecondSensor).isTrue()

        viewModel.promoteSecondSensor()
        val message = withTimeoutOrNull(20_000) {
            while (viewModel.uiState.value.promotionMessage == null) delay(10)
            viewModel.uiState.value.promotionMessage
        }

        assertThat(message).isEqualTo("overview_second_sensor_switch_ok")
        assertThat(viewModel.uiState.value.secondSensorStatus).isNull()
        assertThat(viewModel.uiState.value.canPromoteSecondSensor).isFalse()
    }

    /**
     * The full refresh reads the pump, then the database. Here that read never returns, so the full
     * refresh never writes the state, and only the CGM tick can change it.
     */
    private suspend fun givenFullRefreshNeverEnds() {
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(PumpDescription())
        whenever(pump.batteryLevel).thenReturn(MutableStateFlow<Int?>(null))
        whenever(pump.reservoirLevel).thenReturn(reservoir)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(any())).doSuspendableAnswer { awaitCancellation() }
    }

    private fun givenCgmSource(): FakeStagingBgSource {
        val source = FakeStagingBgSource()
        whenever(activePlugin.activeBgSource).thenReturn(source)
        whenever(dateUtil.now()).thenReturn(NOW)
        return source
    }

    private fun warming(remainingMs: Long?, endsAtEpochMs: Long?, totalMs: Long? = null) = CgmWarmupStatus(
        active = true,
        phase = CgmWarmupStatus.Phase.WARMING,
        remainingMs = remainingMs,
        endsAtEpochMs = endsAtEpochMs,
        totalMs = totalMs
    )

    private fun stagingLifecycle(ageMs: Long) = CgmSensorLifecycle(
        startedAtEpochMs = NOW - ageMs,
        ageMs = ageMs,
        endOfLife = false
    )

    /** Gives each string its resource name, and the arguments in brackets, so a test reads what was chosen. */
    private object NameText : TextResolver {

        override fun gs(ref: TextRef): String = (ref as TextRef.Named).name
        override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref) + args.joinToString(",", "(", ")")
        override fun gsNotLocalised(ref: TextRef): String = gs(ref)
    }

    private class PlainBgSource : BgSource {

        override val sensorBatteryLevel = -1
    }

    private class WarmupOnlyBgSource : BgSource, CgmWarmupProvider {

        override val sensorBatteryLevel = -1
        override val warmupStatus = MutableStateFlow<CgmWarmupStatus?>(null)
    }

    /** BG source with a warm-up and a second sensor slot, so both rows can be built. */
    private class FakeStagingBgSource : BgSource, CgmSensorStatusProvider {

        override val sensorBatteryLevel = -1
        override val warmupStatus = MutableStateFlow<CgmWarmupStatus?>(null)
        override val lifecycle = MutableStateFlow<CgmSensorLifecycle?>(null)
        override val stagingLifecycle = MutableStateFlow<CgmSensorLifecycle?>(null)
        override val stagingState = MutableStateFlow(StagingState.ABSENT)
        override val stagingEvidence = MutableStateFlow<CgmStagingEvidence?>(null)
        override val stagingSettleRemainingMs = MutableStateFlow<Long?>(null)

        var result: PromotionResult = PromotionResult.Ok
        var failure: Exception? = null
        val calls = mutableListOf<Boolean>()

        /** When set, the switch waits here, so a test can tap again while it is still running. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun promoteStagingToProduction(allowEarly: Boolean): PromotionResult {
            synchronized(calls) { calls.add(allowEarly) }
            gate?.await()
            failure?.let { throw it }
            // Like the real source: after a switch there is no second sensor any more.
            if (result == PromotionResult.Ok) stagingState.value = StagingState.ABSENT
            return result
        }
    }

    /** Collects the state the way a screen does. */
    private fun CoroutineScope.show(viewModel: StatusViewModel): Job =
        launch(Dispatchers.Unconfined) { viewModel.uiState.collect { } }

    private suspend fun awaitMore(counter: AtomicInteger, than: Int) =
        withTimeout(5_000) { while (counter.get() <= than) delay(10) }

    /**
     * Waits for [counter] to reach [value] and returns what it has then.
     *
     * These waits are real time, not virtual: the test polls a counter that a coroutine on another
     * dispatcher increments. The timeout is only a ceiling, so a generous one costs nothing when the
     * machine is idle and stops the test failing on a busy one - at 2 s it began failing inside the
     * full module suite while still passing on its own, which says nothing about the behaviour under
     * test and everything about how much else was running.
     */
    private suspend fun awaitValue(counter: AtomicInteger, value: Int): Int =
        withTimeoutOrNull(20_000) { while (counter.get() != value) delay(10); counter.get() } ?: counter.get()

    /** Enough for a whole refresh: a cannula change to count from, no profile, no pump battery. */
    private suspend fun givenStatusData() {
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(PumpDescription())
        whenever(pump.batteryLevel).thenReturn(MutableStateFlow<Int?>(null))
        whenever(pump.reservoirLevel).thenReturn(reservoir)
        val bgSource = mock<BgSource>()
        whenever(activePlugin.activeBgSource).thenReturn(bgSource)
        whenever(bgSource.sensorBatteryLevel).thenReturn(-1)
        whenever(rh.gs(any<TextRef>())).thenReturn("text")
        whenever(dateUtil.computeDiff(any(), any())).thenReturn(TimeDiff(1, 2, 0, 0, 0, 0, 0))
        val cannulaChange = mock<TE>()
        whenever(cannulaChange.timestamp).thenReturn(1_000L)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(any())).thenReturn(null)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).doSuspendableAnswer {
            started.incrementAndGet()
            cannulaChange
        }
        whenever(tddCalculator.calculateIntervalWithCachedDays(any(), any(), any())).thenReturn(TDD(timestamp = 0, totalAmount = 12.0))
        // Only the cannula usage is formatted here (no profile, so no reservoir level), and it is the last
        // step of a refresh, so this marks the moment a refresh has finished.
        whenever(decimalFormatter.to0Decimal(any<Double>())).thenAnswer {
            finished.incrementAndGet()
            "12"
        }
        whenever(rh.gs(eq(CoreUiStrings.value_with_unit), any(), any())).thenReturn(USAGE_TEXT)
    }

    private companion object {

        const val USAGE_TEXT = "12 U"

        /** How long a test waits to be sure that something did NOT happen. */
        const val QUIET_MS = 300L

        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS

        /** Same period as the CGM tick in [StatusViewModel]. */
        const val CGM_TICK_MS = 10_000L
        const val NOW = 1_700_000_000_000L
    }
}
