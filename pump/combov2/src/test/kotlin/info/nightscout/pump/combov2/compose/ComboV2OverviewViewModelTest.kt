package info.nightscout.pump.combov2.compose

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import com.google.common.truth.Truth.assertThat
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.parser.BatteryState
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.combov2.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR
import info.nightscout.comboctl.base.Tbr as ComboCtlTbr
import info.nightscout.comboctl.main.Pump as ComboCtlPump

/**
 * Unit test for [ComboV2OverviewViewModel]'s state assembly. The VM combines ~11 ComboV2Plugin
 * *UIFlow getters (all read at construction) into a [ComboV2OverviewUiState]; the rendering itself
 * is covered by core:ui's PumpOverviewScreenTest.
 *
 * Unlike the Dana VM (whose stateIn seeds buildInitialState()), this VM seeds a plain
 * ComboV2OverviewUiState() default, so uiState.value only exercises buildState() once the flow is
 * collected. The [computeState] helper subscribes (UnconfinedTestDispatcher) to drive buildState().
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ComboV2OverviewViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var context: Context

    private val combov2Plugin: ComboV2Plugin = mock()

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // rx wiring launched by the PumpCommunicationStatus field at construction.
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())

        // The snapshotFlow / displayFrame field-initializers read every *UIFlow getter at
        // construction, so each must return a real StateFlow or construction NPEs. Defaults model an
        // unpaired pump with no data (all optional rows skipped).
        whenever(combov2Plugin.pairedStateUIFlow).thenReturn(MutableStateFlow(false))
        whenever(combov2Plugin.driverStateUIFlow)
            .thenReturn(MutableStateFlow<ComboV2Plugin.DriverState>(ComboV2Plugin.DriverState.NotInitialized))
        whenever(combov2Plugin.lastConnectionTimestampUIFlow).thenReturn(MutableStateFlow<Long?>(null))
        whenever(combov2Plugin.currentActivityUIFlow)
            .thenReturn(MutableStateFlow(ComboV2Plugin.CurrentActivityInfo("", 0.0)))
        whenever(combov2Plugin.batteryStateUIFlow).thenReturn(MutableStateFlow<BatteryState?>(null))
        whenever(combov2Plugin.reservoirLevelUIFlow).thenReturn(MutableStateFlow<ComboV2Plugin.ReservoirLevel?>(null))
        whenever(combov2Plugin.lastBolusUIFlow).thenReturn(MutableStateFlow<ComboCtlPump.LastBolus?>(null))
        whenever(combov2Plugin.currentTbrUIFlow).thenReturn(MutableStateFlow<ComboCtlTbr?>(null))
        whenever(combov2Plugin.baseBasalRateUIFlow).thenReturn(MutableStateFlow<Double?>(null))
        whenever(combov2Plugin.serialNumberUIFlow).thenReturn(MutableStateFlow(""))
        whenever(combov2Plugin.bluetoothAddressUIFlow).thenReturn(MutableStateFlow(""))
        whenever(combov2Plugin.displayFrameUIFlow).thenReturn(MutableStateFlow<DisplayFrame?>(null))

        // Info-row / action labels touched by buildState (unstubbed rh.gs -> null label -> NPE).
        whenever(rh.gs(R.string.combov2_not_paired)).thenReturn("Not paired")
        whenever(rh.gs(R.string.combov2_driver_state_label)).thenReturn("Driver state")
        whenever(rh.gs(CoreUiR.string.disconnected)).thenReturn("Disconnected")
        whenever(rh.gs(CoreUiR.string.pairing)).thenReturn("Pair")
        whenever(rh.gs(CoreUiR.string.pump_unpair)).thenReturn("Unpair")
        whenever(rh.gs(CoreUiR.string.refresh)).thenReturn("Refresh")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ComboV2OverviewViewModel(
        aapsLogger, rh, rxBus, commandQueue, combov2Plugin, ch, context
    )

    /**
     * Subscribes to uiState so the WhileSubscribed stateIn starts its upstream combine and runs
     * buildState(); with the UnconfinedTestDispatcher the combine over the StateFlow sources produces
     * synchronously. Reading uiState.value alone would only return the ComboV2OverviewUiState() seed.
     */
    private fun computeState(viewModel: ComboV2OverviewViewModel): ComboV2OverviewUiState {
        val scope = CoroutineScope(testDispatcher)
        scope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.runCurrent()
        val state = viewModel.uiState.value
        scope.cancel()
        return state
    }

    @Test
    fun construction_seedsEmptyDefaultState() {
        // Without a subscriber, uiState reports the stateIn seed (buildState not yet run). This
        // still proves every construction-time flow getter is stubbed correctly (no NPE).
        val state = createViewModel().uiState.value

        assertThat(state.isPaired).isFalse()
        assertThat(state.overview.infoRows).isEmpty()
        assertThat(state.overview.primaryActions).isEmpty()
        assertThat(state.overview.managementActions).isEmpty()
        assertThat(state.overview.statusBanner).isNull()
    }

    @Test
    fun notPaired_showsNotPairedBanner_andOffersPairing() {
        val state = computeState(createViewModel())

        assertThat(state.isPaired).isFalse()
        assertThat(state.overview.infoRows).isEmpty()
        assertThat(state.overview.primaryActions).isEmpty()
        assertThat(state.overview.statusBanner?.text).isEqualTo("Not paired")
        assertThat(state.overview.statusBanner?.level).isEqualTo(StatusLevel.UNSPECIFIED)
        assertThat(state.overview.managementActions.map { it.label }).containsExactly("Pair")
    }

    @Test
    fun paired_disconnected_buildsDriverRow_enablesRefresh_andOffersUnpair() {
        whenever(combov2Plugin.pairedStateUIFlow).thenReturn(MutableStateFlow(true))
        whenever(combov2Plugin.driverStateUIFlow)
            .thenReturn(MutableStateFlow<ComboV2Plugin.DriverState>(ComboV2Plugin.DriverState.Disconnected))

        val state = computeState(createViewModel())

        assertThat(state.isPaired).isTrue()
        // All optional data is null/empty -> only the driver-state row is built.
        val rows = state.overview.infoRows.filterIsInstance<PumpInfoRow>()
        assertThat(rows).hasSize(1)
        assertThat(rows.first().label).isEqualTo("Driver state")
        assertThat(rows.first().value).isEqualTo("Disconnected")
        assertThat(rows.first().level).isEqualTo(StatusLevel.NORMAL)

        // Disconnected -> refresh is enabled.
        val refresh = state.overview.primaryActions.single()
        assertThat(refresh.label).isEqualTo("Refresh")
        assertThat(refresh.enabled).isTrue()

        // Paired -> management offers Unpair, not Pair.
        assertThat(state.overview.managementActions.map { it.label }).containsExactly("Unpair")
        assertThat(state.currentActivityText).isEqualTo("")
    }
}
