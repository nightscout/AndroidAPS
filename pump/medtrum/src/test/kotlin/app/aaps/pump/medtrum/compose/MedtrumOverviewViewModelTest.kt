package app.aaps.pump.medtrum.compose

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.ConnectionState
import app.aaps.pump.medtrum.comm.enums.AlarmState
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.EnumSet
import app.aaps.core.ui.R as CoreUiR

/**
 * Unit test for [MedtrumOverviewViewModel]'s initial [PumpOverviewUiState] assembly.
 *
 * The VM owns its OWN CoroutineScope (Dispatchers.Default + SupervisorJob) rather than using
 * viewModelScope, and `uiState` is a `combine(...).stateIn(scope, WhileSubscribed(...), buildInitialState())`.
 * Reading `uiState.value` returns `buildInitialState()` WITHOUT running the combine (no subscriber),
 * so we only need the flow getters (read while building the combine argument array) plus everything
 * `buildInitialState()`/`buildUiState()` touch. No Dispatchers.setMain is required.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MedtrumOverviewViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var context: Context

    private val medtrumPlugin: MedtrumPlugin = mock()
    private val medtrumPump: MedtrumPump = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        // The uiState field-initializer calls combine(...) over these non-null StateFlow getters,
        // so they must be stubbed or the getter reads NPE. bolusAmountDeliveredFlow.value is also
        // read directly by buildInitialState().
        whenever(medtrumPump.connectionStateFlow).thenReturn(MutableStateFlow(ConnectionState.DISCONNECTED))
        whenever(medtrumPump.pumpStateFlow).thenReturn(MutableStateFlow(MedtrumPumpState.NONE))
        whenever(medtrumPump.lastBasalTypeFlow).thenReturn(MutableStateFlow(BasalType.NONE))
        whenever(medtrumPump.lastBasalRateFlow).thenReturn(MutableStateFlow(0.0))
        whenever(medtrumPump.reservoirFlow).thenReturn(MutableStateFlow(0.0))
        whenever(medtrumPump.batteryVoltage_BFlow).thenReturn(MutableStateFlow(0.0))
        whenever(medtrumPump.bolusAmountDeliveredFlow).thenReturn(MutableStateFlow(0.0))
        whenever(medtrumPump.lastBolusTimeFlow).thenReturn(MutableStateFlow<Long?>(null))
        whenever(medtrumPump.lastBolusAmountFlow).thenReturn(MutableStateFlow<Double?>(null))
        whenever(medtrumPump.lastConnectionFlow).thenReturn(MutableStateFlow(0L))

        // Property getters read directly by buildInitialState() (default mock values are safe for the rest)
        whenever(medtrumPump.lastBasalType).thenReturn(BasalType.NONE)
        whenever(medtrumPump.swVersion).thenReturn("")   // read into an info row; null -> NPE
        // EnumSet is not auto-stubbed to empty by Mockito (returns null) -> must stub or activeAlarms.map NPEs
        whenever(medtrumPump.activeAlarms).thenReturn(EnumSet.noneOf(AlarmState::class.java))

        // PumpCommunicationStatus init subscribes to these two flows at construction
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())

        // Formatting collaborator (base basal rate row is always built)
        whenever(ch.basalRateString(any(), any(), any())).thenReturn("0.00 U/h")

        // Every info-row / action / banner label the build path touches (unstubbed rh.gs -> null -> NPE in PumpInfoRow)
        // Common rows (core:ui builder)
        whenever(rh.gs(CoreUiR.string.tempbasal_label)).thenReturn("Temp basal")
        whenever(rh.gs(CoreUiR.string.extended_bolus_label)).thenReturn("Extended bolus")
        whenever(rh.gs(CoreUiR.string.serial_number)).thenReturn("Serial number")
        // Medtrum-specific rows
        whenever(rh.gs(R.string.pump_state_label)).thenReturn("Pump state")
        whenever(rh.gs(CoreUiR.string.base_basal_rate_label)).thenReturn("Base basal")
        whenever(rh.gs(R.string.pump_type_label)).thenReturn("Pump type")
        whenever(rh.gs(R.string.patch_no_label)).thenReturn("Patch no")
        whenever(rh.gs(R.string.expiry_not_enabled)).thenReturn("")   // empty -> expiry row skipped
        // pumpState.label values used across the two tests
        whenever(rh.gs(R.string.alarm_none)).thenReturn("None")
        whenever(rh.gs(R.string.status_active)).thenReturn("Active")
        // Banner
        whenever(rh.gs(R.string.patch_not_active)).thenReturn("Patch not activated")
        // Actions
        whenever(rh.gs(CoreUiR.string.refresh)).thenReturn("Refresh")
        whenever(rh.gs(R.string.reset_alarms_label)).thenReturn("Reset alarms")
        whenever(rh.gs(R.string.change_patch_label)).thenReturn("Change patch")
        whenever(rh.gs(CoreUiR.string.pump_unpair)).thenReturn("Unpair")
    }

    private fun createViewModel() = MedtrumOverviewViewModel(
        aapsLogger, rh, profileFunction, commandQueue, rxBus, dateUtil,
        medtrumPlugin, medtrumPump, ch, preferences, uel, context
    )

    @Test
    fun notActive_showsPatchNotActiveBanner_disablesRefresh_andNoUnpair() {
        whenever(medtrumPump.connectionState).thenReturn(ConnectionState.DISCONNECTED)
        whenever(medtrumPump.pumpState).thenReturn(MedtrumPumpState.NONE)   // NONE -> "patch not active" banner, not pump-active
        whenever(medtrumPump.pumpSN).thenReturn(0L)                          // not paired -> no Unpair

        val state = createViewModel().uiState.value

        assertThat(state.infoRows).isNotEmpty()
        assertThat(state.statusBanner).isNotNull()
        assertThat(state.statusBanner?.level).isEqualTo(StatusLevel.WARNING)
        assertThat(state.statusBanner?.text).isEqualTo("Patch not activated")
        // NONE is not (EJECTED < state < STOPPED) -> refresh disabled
        assertThat(state.primaryActions.first { it.label == "Refresh" }.enabled).isFalse()
        assertThat(state.managementActions.map { it.label }).contains("Change patch")
        assertThat(state.managementActions.map { it.label }).doesNotContain("Unpair")
    }

    @Test
    fun activeAndPaired_noBanner_enablesRefresh_andOffersUnpair() {
        whenever(medtrumPump.connectionState).thenReturn(ConnectionState.DISCONNECTED)
        whenever(medtrumPump.pumpState).thenReturn(MedtrumPumpState.ACTIVE)  // EJECTED < ACTIVE < STOPPED -> pump active, no banner
        whenever(medtrumPump.pumpSN).thenReturn(12345L)                      // paired -> Unpair offered

        val state = createViewModel().uiState.value

        assertThat(state.statusBanner).isNull()
        // disconnected + pump-active -> refresh enabled
        assertThat(state.primaryActions.first { it.label == "Refresh" }.enabled).isTrue()
        assertThat(state.managementActions.map { it.label }).contains("Unpair")
    }
}
