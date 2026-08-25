package app.aaps.pump.diaconn.compose

import android.content.Context
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.events.EventDiaconnG8NewStatus
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR

/**
 * Unit test for [DiaconnOverviewViewModel] state assembly. Constructs the VM directly with mocked
 * ctor deps and asserts the initial [app.aaps.core.ui.compose.pump.PumpOverviewUiState] produced by
 * buildInitialState(). The rendering itself is covered by core:ui's PumpOverviewScreenTest
 * (DiaconnOverviewScreen just delegates to PumpOverviewScreen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DiaconnOverviewViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var aapsSchedulers: AapsSchedulers
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var context: Context

    private val diaconnG8Pump: DiaconnG8Pump = mock()
    private val activePump: PumpWithConcentration = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // The pumpDataFlow field-initializer calls combine(...) over these non-null StateFlow getters,
        // so they must be stubbed or construction NPEs. The flows themselves are only collected on
        // subscription — uiState.value returns buildInitialState() until then.
        whenever(diaconnG8Pump.lastConnectionFlow).thenReturn(MutableStateFlow(0L))
        whenever(diaconnG8Pump.systemRemainInsulinFlow).thenReturn(MutableStateFlow(0.0))
        whenever(diaconnG8Pump.systemRemainBatteryFlow).thenReturn(MutableStateFlow<Int?>(null))
        whenever(diaconnG8Pump.lastBolusTimeFlow).thenReturn(MutableStateFlow<Long?>(null))
        whenever(diaconnG8Pump.lastBolusAmountFlow).thenReturn(MutableStateFlow<Double?>(null))

        // rx wiring touched at construction (PumpCommunicationStatus + init subscriptions)
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toObservable(EventDiaconnG8NewStatus::class.java)).thenReturn(Observable.empty())
        whenever(rxBus.toObservable(EventInitializationChanged::class.java)).thenReturn(Observable.empty())
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(persistenceLayer.observeChanges(EB::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TB::class.java)).thenReturn(emptyFlow())

        // formatting collaborators (called during buildUiState before the isConfigured branch)
        whenever(ch.fromPump(any(), any())).thenReturn(0.0)
        whenever(ch.basalRateString(any(), any(), any())).thenReturn("0.00 U/h")
        whenever(ch.insulinAmountString(any())).thenReturn("0 U")

        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePump.baseBasalRate).thenReturn(PumpRate(0.0))

        // action labels (built regardless of configured state)
        whenever(rh.gs(CoreUiR.string.refresh)).thenReturn("Refresh")
        whenever(rh.gs(CoreUiR.string.pump_history)).thenReturn("History")
        whenever(rh.gs(R.string.diaconng8_pump_settings)).thenReturn("Settings")
        whenever(rh.gs(CoreUiR.string.pump_pair)).thenReturn("Pair")
        whenever(rh.gs(CoreUiR.string.pump_unpair)).thenReturn("Unpair")

        // info-row labels rendered in the configured state (unstubbed -> null label -> NPE)
        whenever(rh.gs(CoreUiR.string.serial_number)).thenReturn("Serial number")
        whenever(rh.gs(CoreUiR.string.battery_label)).thenReturn("Battery")
        whenever(rh.gs(CoreUiR.string.daily_units)).thenReturn("Daily units")
        whenever(rh.gs(CoreUiR.string.max_daily_units)).thenReturn("Max daily units")
        whenever(rh.gs(CoreUiR.string.base_basal_rate_label)).thenReturn("Base basal")
        whenever(rh.gs(CoreUiR.string.tempbasal_label)).thenReturn("Temp basal")
        whenever(rh.gs(CoreUiR.string.extended_bolus_label)).thenReturn("Extended bolus")
        whenever(rh.gs(CoreUiR.string.reservoir_label)).thenReturn("Reservoir")
        whenever(rh.gs(CoreUiR.string.firmware)).thenReturn("Firmware")
        whenever(rh.gs(R.string.basal_step)).thenReturn("Basal step")
        whenever(rh.gs(R.string.bolus_step)).thenReturn("Bolus step")
        whenever(rh.gs(R.string.diaconn_g8_pump)).thenReturn("Diaconn G8")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DiaconnOverviewViewModel(
        aapsLogger, rh, rxBus, aapsSchedulers, commandQueue, dateUtil, diaconnG8Pump,
        activePlugin, persistenceLayer, uel, preferences, ch, context
    )

    @Test
    fun notConfigured_hasNoInfoRows_andOffersPairing() {
        whenever(activePump.isConfigured()).thenReturn(false)
        whenever(activePump.isInitialized()).thenReturn(false)

        val state = createViewModel().uiState.value

        assertThat(state.infoRows).isEmpty()
        assertThat(state.statusBanner).isNull()
        assertThat(state.primaryActions.map { it.label }).containsExactly("Refresh", "History").inOrder()
        // management offers pump settings + Pair when not configured
        assertThat(state.managementActions.map { it.label }).containsExactly("Settings", "Pair").inOrder()
    }

    @Test
    fun configured_buildsInfoRows_andBatteryWarnLevel() {
        whenever(activePump.isConfigured()).thenReturn(true)
        whenever(activePump.isInitialized()).thenReturn(true)
        whenever(diaconnG8Pump.systemRemainBattery).thenReturn(40)   // 27..51 -> WARNING

        val state = createViewModel().uiState.value

        assertThat(state.infoRows).isNotEmpty()
        val batteryRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Battery" }
        assertThat(batteryRow.level).isEqualTo(StatusLevel.WARNING)
        // configured -> management offers Unpair, not Pair
        assertThat(state.managementActions.map { it.label }).contains("Unpair")
    }
}
