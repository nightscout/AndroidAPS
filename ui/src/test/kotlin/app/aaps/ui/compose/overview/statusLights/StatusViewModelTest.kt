package app.aaps.ui.compose.overview.statusLights

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class StatusViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var insulin: Insulin
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
        whenever(rxBus.toFlow(EventInitializationChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventNsClientStatusUpdated::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.databaseClearedFlow).thenReturn(emptyFlow())
        sut = StatusViewModel(
            rh, activePlugin, insulin, config, persistenceLayer, dateUtil, rxBus, preferences,
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
}
