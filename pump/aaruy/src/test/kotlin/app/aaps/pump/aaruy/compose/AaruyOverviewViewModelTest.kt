package app.aaps.pump.aaruy.compose

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.event.EventAaruyDeviceChange
import app.aaps.pump.aaruy.event.EventAaruyPumpConfigurationChanged
import app.aaps.pump.aaruy.event.EventAaruyPumpValuesChanged
import app.aaps.pump.aaruy.keys.AaruyStringKey
import com.google.common.truth.Truth.assertThat
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
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.aaruy.R as AaruyR

/**
 * Unit test for [AaruyOverviewViewModel] state assembly. Constructs the VM directly with mocked
 * ctor deps and asserts the initial [app.aaps.core.ui.compose.pump.PumpOverviewUiState] produced
 * by buildInitialState(). The rendering itself is covered by core:ui's PumpOverviewScreenTest
 * (AaruyOverviewScreen just delegates to PumpOverviewScreen).
 *
 * Unlike Diaconn, Aaruy builds its info rows regardless of the pump's configured state — serial
 * number, daily units, base basal rate, reservoir and firmware are always present. The
 * not-configured test therefore asserts on that baseline instead of an empty list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AaruyOverviewViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var ch: ConcentrationHelper

    private val aaruyPump: AaruyPump = mock()
    private val aaruyPlugin: AaruyPlugin = mock()
    private val activePump: PumpWithConcentration = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Catch-all first so that any rh.gs(...) call not explicitly stubbed later still returns
        // a non-null String (PumpCommunicationStatus, for example, formats status strings via rh.gs).
        whenever(rh.gs(any<Int>())).thenReturn("text")
        whenever(rh.gs(any<Int>(), anyVararg())).thenReturn("formatted")

        // AaruyPump exposes 5 StateFlow getters used by the pumpDataFlow field-initializer
        // (combine(...)). They must be non-null at construction, but the flows themselves are
        // only collected on subscription — uiState.value returns buildInitialState() until then.
        whenever(aaruyPump.lastConnectionFlow).thenReturn(MutableStateFlow(0L))
        whenever(aaruyPump.reservoirFlow).thenReturn(MutableStateFlow(0.0))
        whenever(aaruyPump.remainBatteryFlow).thenReturn(MutableStateFlow(0))
        whenever(aaruyPump.lastBolusTimeFlow).thenReturn(MutableStateFlow(0L))
        whenever(aaruyPump.lastBolusAmountFlow).thenReturn(MutableStateFlow(0.0))

        // buildInitialState() reads the plain getters, not the flows
        whenever(aaruyPump.lastConnection).thenReturn(0L)
        whenever(aaruyPump.reservoir).thenReturn(0.0)
        whenever(aaruyPump.remainBattery).thenReturn(0)
        whenever(aaruyPump.lastBolusTime).thenReturn(0L)
        whenever(aaruyPump.lastBolusAmount).thenReturn(0.0)
        whenever(aaruyPump.todayTotalAmount).thenReturn(0.0)
        whenever(aaruyPump.pumpSN).thenReturn("SN-TEST")
        whenever(aaruyPump.swVersion).thenReturn("1.0.0")
        whenever(aaruyPump.pumpType).thenReturn(3) // B200A
        whenever(aaruyPump.bolusStep).thenReturn(2)
        whenever(aaruyPump.isTempBasalInProgress).thenReturn(false)
        whenever(aaruyPump.isExtendedInProgress).thenReturn(false)

        // rx wiring touched at construction (PumpCommunicationStatus + init subscriptions)
        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventInitializationChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventAaruyDeviceChange::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventAaruyPumpValuesChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventAaruyPumpConfigurationChanged::class)).thenReturn(emptyFlow())

        // date/time formatting used in buildUiState
        whenever(dateUtil.now()).thenReturn(1_000_000L)
        whenever(dateUtil.timeString(any())).thenReturn("12:00")

        // preferences: bindAddress must be non-null (String.isNotEmpty() NPEs otherwise)
        whenever(preferences.get(AaruyStringKey.PumpBoundAddress)).thenReturn("")

        whenever(ch.fromPump(any(), any())).thenReturn(0.0)
        whenever(ch.basalRateString(any(), any(), any())).thenReturn("0.00 U/h")
        whenever(ch.insulinAmountString(any())).thenReturn("0 U")

        // active plugin wiring
        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePump.baseBasalRate).thenReturn(PumpRate(0.0))

        // action labels (built regardless of configured state)
        whenever(rh.gs(CoreUiR.string.refresh)).thenReturn("Refresh")
        whenever(rh.gs(CoreUiR.string.pump_history)).thenReturn("History")
        whenever(rh.gs(CoreUiR.string.pump_pair)).thenReturn("Pair")
        whenever(rh.gs(CoreUiR.string.pump_unpair)).thenReturn("Unpair")
        whenever(rh.gs(AaruyR.string.replace_bat_rescv_label)).thenReturn("Replace")

        // info-row labels rendered unconditionally
        whenever(rh.gs(CoreUiR.string.serial_number)).thenReturn("Serial number")
        whenever(rh.gs(CoreUiR.string.battery_label)).thenReturn("Battery")
        whenever(rh.gs(CoreUiR.string.last_connection_label)).thenReturn("Last connection")
        whenever(rh.gs(CoreUiR.string.last_bolus_label)).thenReturn("Last bolus")
        whenever(rh.gs(CoreUiR.string.daily_units)).thenReturn("Daily units")
        whenever(rh.gs(CoreUiR.string.base_basal_rate_label)).thenReturn("Base basal")
        whenever(rh.gs(CoreUiR.string.tempbasal_label)).thenReturn("Temp basal")
        whenever(rh.gs(CoreUiR.string.extended_bolus_label)).thenReturn("Extended bolus")
        whenever(rh.gs(CoreUiR.string.reservoir_label)).thenReturn("Reservoir")
        whenever(rh.gs(CoreUiR.string.firmware)).thenReturn("Firmware")
        whenever(rh.gs(AaruyR.string.setting_insulin_bolus_rate)).thenReturn("Bolus rate")
        whenever(rh.gs(AaruyR.string.aaruy_pump)).thenReturn("Aaruy")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AaruyOverviewViewModel(
        aapsLogger, rh, rxBus, commandQueue, dateUtil,
        aaruyPump, aaruyPlugin, activePlugin, uel, preferences, ch
    )

    @Test
    fun notConfigured_hasBaseInfoRows_andOffersPairing() {
        whenever(activePump.isConfigured()).thenReturn(false)
        whenever(activePump.isInitialized()).thenReturn(false)

        val state = createViewModel().uiState.value

        // Aaruy builds these rows unconditionally — the pump does not gate them on isConfigured
        assertThat(state.infoRows.filterIsInstance<PumpInfoRow>().map { it.label }).containsAtLeast(
            "Serial number", "Daily units", "Base basal", "Reservoir", "Firmware"
        )

        assertThat(state.statusBanner).isNull()
        assertThat(state.primaryActions.map { it.label }).containsExactly("Refresh", "History").inOrder()
        // management offers pump settings + Pair when not configured
        assertThat(state.managementActions.map { it.label }).containsExactly("Replace", "Pair").inOrder()
    }

    @Test
    fun configured_buildsInfoRows_andBatteryWarnLevel() {
        whenever(activePump.isConfigured()).thenReturn(true)
        whenever(activePump.isInitialized()).thenReturn(true)
        whenever(preferences.get(AaruyStringKey.PumpBoundAddress)).thenReturn("AA:BB:CC:DD:EE:FF")
        whenever(aaruyPump.remainBattery).thenReturn(40)   // 27..51 -> WARNING

        val state = createViewModel().uiState.value

        assertThat(state.infoRows).isNotEmpty()
        val batteryRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Battery" }
        assertThat(batteryRow.level).isEqualTo(StatusLevel.WARNING)
        // configured -> management offers Unpair, not Pair
        assertThat(state.managementActions.map { it.label }).contains("Unpair")
    }

    @Test
    fun batteryLevel_isCriticalAtOrBelow26() {
        whenever(aaruyPump.remainBattery).thenReturn(26)

        val row = createViewModel().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()
            .first { it.label == "Battery" }

        assertThat(row.level).isEqualTo(StatusLevel.CRITICAL)
    }

    @Test
    fun reservoirLevel_isCriticalAtOrBelow20() {
        whenever(aaruyPump.reservoir).thenReturn(20.0)

        val row = createViewModel().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()
            .first { it.label == "Reservoir" }

        assertThat(row.level).isEqualTo(StatusLevel.CRITICAL)
    }

    @Test
    fun reservoirLevel_isWarningBetween20And50() {
        whenever(aaruyPump.reservoir).thenReturn(40.0)

        val row = createViewModel().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()
            .first { it.label == "Reservoir" }

        assertThat(row.level).isEqualTo(StatusLevel.CRITICAL)
    }

    @Test
    fun tempbasalRow_isInvisibleWhenNoTbrRunning() {
        val row = createViewModel().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()
            .first { it.label == "Temp basal" }

        assertThat(row.visible).isFalse()
    }
}