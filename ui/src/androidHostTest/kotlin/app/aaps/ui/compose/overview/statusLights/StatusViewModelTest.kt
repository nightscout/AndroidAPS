package app.aaps.ui.compose.overview.statusLights

import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TimeDiff
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.ui.UiStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private fun newViewModel() = StatusViewModel(
        rh, activePlugin, profileFunction, config, persistenceLayer, dateUtil, rxBus, preferences,
        tddCalculator, decimalFormatter, processedDeviceStatusData
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
        whenever(rh.gs(eq(UiStrings.status_insulin_level), any())).thenReturn(USAGE_TEXT)
    }

    private companion object {

        const val USAGE_TEXT = "12 U"

        /** How long a test waits to be sure that something did NOT happen. */
        const val QUIET_MS = 300L
    }
}
