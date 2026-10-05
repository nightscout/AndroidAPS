package app.aaps.ui.compose.overview.statusLights

import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TimeDiff
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

    private lateinit var sut: StatusViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{}-launched refreshState() coroutine (no advanceUntilIdle),
        // so construction stays clean and we test the default uiState. The event-listener flows are still
        // built synchronously in init, so they must be non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(rxBus.toFlow(EventInitializationChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventNsClientStatusUpdated::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.databaseClearedFlow).thenReturn(emptyFlow())
        sut = StatusViewModel(
            rh, activePlugin, profileFunction, config, persistenceLayer, dateUtil, rxBus, preferences,
            tddCalculator, decimalFormatter, processedDeviceStatusData
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has no status items and hidden actions`() {
        val state = sut.uiState.value
        assertThat(state.sensorStatus).isNull()
        assertThat(state.insulinStatus).isNull()
        assertThat(state.showFill).isFalse()
        assertThat(state.showPumpBatteryChange).isFalse()
        assertThat(state.isPatchPump).isFalse()
    }

    /**
     * Every pump status event calls `refreshState()`, and a Dana status read sends many in a row. Each
     * call used to start its own cannula usage calculation and none was cancelled, so they piled up on
     * the database. Only one may run at a time: a newer refresh cancels the older one.
     */
    @Test
    fun `a new refresh cancels the cannula usage calculation still running`() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        givenStatusData()
        val started = AtomicInteger(0)
        val running = AtomicInteger(0)
        val finished = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        whenever(tddCalculator.calculateIntervalWithCachedDays(any(), any(), any())).doSuspendableAnswer {
            started.incrementAndGet()
            running.incrementAndGet()
            try {
                gate.await()
            } finally {
                running.decrementAndGet()
            }
            finished.incrementAndGet()
            TDD(timestamp = 0, totalAmount = 12.0)
        }
        // init and the first tick of the minute ticker both refresh
        val viewModel = StatusViewModel(
            rh, activePlugin, profileFunction, config, persistenceLayer, dateUtil, rxBus, preferences,
            tddCalculator, decimalFormatter, processedDeviceStatusData
        )
        awaitMore(started, 0)
        assertThat(awaitValue(running, 1)).isEqualTo(1)

        // Three pump status events while the calculation is still running. Each must end the one before.
        repeat(3) {
            val before = started.get()
            viewModel.refreshState()
            awaitMore(started, before)
            assertThat(awaitValue(running, 1)).isEqualTo(1)
        }

        gate.complete(Unit)
        assertThat(awaitValue(running, 0)).isEqualTo(0)
        assertThat(finished.get()).isEqualTo(1)
    }

    private suspend fun awaitMore(counter: AtomicInteger, than: Int) =
        withTimeout(5_000) { while (counter.get() <= than) delay(10) }

    /** Waits up to 2 s for [counter] to reach [value] and returns what it has then. */
    private suspend fun awaitValue(counter: AtomicInteger, value: Int): Int =
        withTimeoutOrNull(2_000) { while (counter.get() != value) delay(10); counter.get() } ?: counter.get()

    /** Enough for a whole refresh: a cannula change to count from, no profile, no pump battery. */
    private suspend fun givenStatusData() {
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(PumpDescription())
        whenever(pump.batteryLevel).thenReturn(MutableStateFlow<Int?>(null))
        val bgSource = mock<BgSource>()
        whenever(activePlugin.activeBgSource).thenReturn(bgSource)
        whenever(bgSource.sensorBatteryLevel).thenReturn(-1)
        whenever(rh.gs(any<TextRef>())).thenReturn("text")
        whenever(dateUtil.computeDiff(any(), any())).thenReturn(TimeDiff(1, 2, 0, 0, 0, 0, 0))
        val cannulaChange = mock<TE>()
        whenever(cannulaChange.timestamp).thenReturn(1_000L)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(any())).thenReturn(null)
        whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)).thenReturn(cannulaChange)
    }
}
