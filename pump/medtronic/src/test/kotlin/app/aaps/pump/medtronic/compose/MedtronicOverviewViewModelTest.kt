package app.aaps.pump.medtronic.compose

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtronic.R
import app.aaps.pump.medtronic.defs.BatteryType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import app.aaps.pump.medtronic.events.EventMedtronicPumpValuesChanged
import app.aaps.pump.medtronic.util.MedtronicUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.inject.Provider
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.common.hw.rileylink.R as RileyLinkR

/**
 * Unit test for [MedtronicOverviewViewModel]. Constructs the VM directly with mocked ctor deps and
 * asserts the initial [app.aaps.core.ui.compose.pump.PumpOverviewUiState] produced by buildUiState()
 * (evaluated eagerly as the stateIn initialValue). The rendering itself is covered by core:ui's
 * PumpOverviewScreenTest (MedtronicOverviewScreen just delegates to PumpOverviewScreen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MedtronicOverviewViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var context: Context

    private val ch: ConcentrationHelper = mock()
    private val medtronicPumpPlugin: MedtronicPumpPlugin = mock()
    private val medtronicPumpStatus: MedtronicPumpStatus = mock()
    private val medtronicUtil: MedtronicUtil = mock()
    private val rileyLinkServiceData: RileyLinkServiceData = mock()
    private val serviceTaskExecutor: ServiceTaskExecutor = mock()
    private val resetTaskProvider: Provider<ResetRileyLinkConfigurationTask> = mock()
    private val wakeTaskProvider: Provider<WakeAndTuneTask> = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Catch-all so every info-row / action label resolves to a non-null String (PumpInfoRow.label
        // and PumpAction.label are non-null; an unstubbed rh.gs() would return null and NPE at build).
        whenever(rh.gs(anyInt())).thenReturn("txt")

        // rx wiring touched at construction: PumpCommunicationStatus init + the three medtronicRefresh
        // collectors launched in viewModelScope (UnconfinedTestDispatcher runs them eagerly).
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventMedtronicPumpValuesChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventRileyLinkDeviceStatusChange::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventMedtronicPumpConfigurationChanged::class.java)).thenReturn(emptyFlow())

        // Pump state read by buildUiState() -> buildInfoRows() (kept minimal to skip optional branches).
        whenever(rileyLinkServiceData.rileyLinkServiceState).thenReturn(RileyLinkServiceState.NotStarted)
        whenever(medtronicPumpStatus.pumpDeviceState).thenReturn(PumpDeviceState.NeverContacted)
        whenever(medtronicPumpStatus.activeProfileName).thenReturn("STD")
        whenever(medtronicPumpStatus.lastConnection).thenReturn(0L)      // -> "-" (skips ago formatting)
        whenever(medtronicPumpStatus.batteryType).thenReturn(BatteryType.None)
        whenever(medtronicPumpStatus.errorInfo).thenReturn("-")          // PLACEHOLDER -> NORMAL level
        whenever(medtronicPumpPlugin.baseBasalRate).thenReturn(PumpRate(0.0))
        // medtronicPumpPlugin.rileyLinkService defaults to null -> isConfigured = false

        // formatting collaborators
        whenever(ch.basalRateString(any(), any(), any())).thenReturn("0.00 U/h")
        whenever(ch.insulinAmountString(any())).thenReturn("0 U")
        whenever(ch.fromPump(any(), any())).thenReturn(0.0)              // reservoir <= 20 -> CRITICAL
        whenever(ch.basalTbrString(any(), any(), any(), any(), any(), any())).thenReturn("")  // temp-basal text (else null -> NPE)

        // labels asserted on
        whenever(rh.gs(CoreUiR.string.refresh)).thenReturn("Refresh")
        whenever(rh.gs(RileyLinkR.string.rileylink_pair)).thenReturn("Pair")
        whenever(rh.gs(CoreUiR.string.pump_history)).thenReturn("History")
        whenever(rh.gs(R.string.riley_statistics)).thenReturn("Statistics")
        whenever(rh.gs(R.string.medtronic_custom_action_wake_and_tune)).thenReturn("Wake and tune")
        whenever(rh.gs(R.string.medtronic_custom_action_clear_bolus_block)).thenReturn("Clear bolus block")
        whenever(rh.gs(R.string.medtronic_custom_action_reset_rileylink)).thenReturn("Reset RileyLink")
        whenever(rh.gs(CoreUiR.string.battery_label)).thenReturn("Battery")
        whenever(rh.gs(CoreUiR.string.reservoir_label)).thenReturn("Reservoir")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MedtronicOverviewViewModel(
        rh, ch, medtronicPumpPlugin, medtronicPumpStatus, medtronicUtil, rileyLinkServiceData,
        serviceTaskExecutor, commandQueue, rxBus, dateUtil, aapsLogger, resetTaskProvider,
        wakeTaskProvider, context
    )

    @Test
    fun buildsInfoRows_andManagementActions() {
        val state = createViewModel().uiState.value

        assertThat(state.statusBanner).isNull()
        assertThat(state.queueStatus).isNull()
        assertThat(state.infoRows.filterIsInstance<PumpInfoRow>()).isNotEmpty()
        assertThat(state.primaryActions.map { it.label }).containsExactly("Refresh")
        assertThat(state.managementActions.map { it.label })
            .containsExactly("Pair", "History", "Statistics", "Wake and tune", "Clear bolus block", "Reset RileyLink")
    }

    @Test
    fun infoRows_reflectBatteryWarnAndReservoirCriticalLevels() {
        whenever(medtronicPumpStatus.batteryRemaining).thenReturn(20)   // 11..25 -> WARNING

        val state = createViewModel().uiState.value
        val rows = state.infoRows.filterIsInstance<PumpInfoRow>()

        val batteryRow = rows.first { it.label == "Battery" }
        assertThat(batteryRow.level).isEqualTo(StatusLevel.WARNING)

        val reservoirRow = rows.first { it.label == "Reservoir" }
        assertThat(reservoirRow.level).isEqualTo(StatusLevel.CRITICAL)
    }
}
