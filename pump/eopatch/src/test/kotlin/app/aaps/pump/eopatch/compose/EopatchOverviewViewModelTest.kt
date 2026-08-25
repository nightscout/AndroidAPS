package app.aaps.pump.eopatch.compose

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.TempBasalManager
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import app.aaps.pump.eopatch.R
import app.aaps.core.ui.R as CoreUiR

/**
 * Unit test for [EopatchOverviewViewModel]'s PumpOverviewUiState assembly.
 *
 * The VM extends androidx ViewModel (uses viewModelScope only in click handlers, not exercised here)
 * but drives uiState with its OWN internal CoroutineScope. Reading uiState.value returns the
 * stateIn seed value produced by buildUiState() at construction, without collecting the combine.
 * The rendering itself is covered by core:ui's PumpOverviewScreenTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EopatchOverviewViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var patchManager: IPatchManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var aapsSchedulers: AapsSchedulers
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var context: Context

    // Concrete final collaborators (Mockito 5 inline handles final classes)
    private val patchManagerExecutor: PatchManagerExecutor = mock()
    private val patchConfig: PatchConfig = mock()
    private val tempBasalManager: TempBasalManager = mock()
    private val normalBasalManager: NormalBasalManager = mock()
    private val preferenceManager: PreferenceManager = mock()

    // Default zero-state patch state: all boolean flags false, remainedInsulin 0f, updatedTimestamp 0.
    private val patchState = PatchState()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Construction reads (field-initializers): patchManagerExecutor.patchConnectionState and
        // preferenceManager.patchState.{isNormalBasalPaused,remainedInsulin,updatedTimestamp}.
        whenever(patchManagerExecutor.patchConnectionState).thenReturn(BleConnectionState.CONNECTED)
        whenever(preferenceManager.patchState).thenReturn(patchState)

        // init { } bridges four RxJava streams onto StateFlows, observed on aapsSchedulers.main.
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(preferenceManager.observePatchConfig()).thenReturn(Observable.empty())
        whenever(preferenceManager.observePatchState()).thenReturn(Observable.empty())
        whenever(preferenceManager.observePatchLifeCycle()).thenReturn(Observable.empty())
        whenever(patchManagerExecutor.observePatchConnectionState()).thenReturn(Observable.empty())

        // PumpCommunicationStatus field-initializer subscribes to these rx flows at construction.
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())

        // buildUiState computes insulinText unconditionally (before the isActivated guard).
        whenever(ch.insulinAmountString(any())).thenReturn("0 U")

        // Serial number empty -> serial row skipped in both scenarios.
        whenever(patchConfig.patchSerialNumber).thenReturn("")

        // Info-row / action / banner labels touched by buildUiState (unstubbed -> null -> NPE).
        whenever(rh.gs(CoreUiR.string.tempbasal_label)).thenReturn("Temp basal")
        whenever(rh.gs(CoreUiR.string.extended_bolus_label)).thenReturn("Extended bolus")
        whenever(rh.gs(CoreUiR.string.status)).thenReturn("Status")
        whenever(rh.gs(CoreUiR.string.reservoir_label)).thenReturn("Reservoir")
        whenever(rh.gs(CoreUiR.string.pump_suspend)).thenReturn("Suspend")
        whenever(rh.gs(CoreUiR.string.pump_resume)).thenReturn("Resume")
        whenever(rh.gs(R.string.eopatch_not_activated)).thenReturn("Not activated")
        whenever(rh.gs(R.string.string_activate_patch)).thenReturn("Activate Patch")
        whenever(rh.gs(R.string.string_running)).thenReturn("Running")
        whenever(rh.gs(R.string.string_discard_patch)).thenReturn("Discard Patch")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = EopatchOverviewViewModel(
        rh, patchManager, patchManagerExecutor, patchConfig, tempBasalManager, normalBasalManager,
        preferenceManager, profileFunction, aapsSchedulers, dateUtil, pumpSync, commandQueue, rxBus, ch, context
    )

    @Test
    fun notActivated_showsNotActivatedBanner_andOffersActivate() {
        whenever(patchConfig.isActivated).thenReturn(false)

        val state = createViewModel().uiState.value

        // Not-activated warning banner (communicationStatus banner is null -> falls back to this one).
        assertThat(state.statusBanner).isNotNull()
        assertThat(state.statusBanner!!.text).isEqualTo("Not activated")
        assertThat(state.statusBanner!!.level).isEqualTo(StatusLevel.WARNING)

        // Common rows are always emitted but the temp-basal / extended-bolus rows are invisible here,
        // and no activated-only rows are added while connected -> no VISIBLE info rows.
        val visibleRows = state.infoRows.filterIsInstance<PumpInfoRow>().filter { it.visible }
        assertThat(visibleRows).isEmpty()

        assertThat(state.primaryActions.map { it.label }).containsExactly("Activate Patch")
        assertThat(state.managementActions).isEmpty()
    }

    @Test
    fun activated_running_showsStatusRow_suspendAction_andDiscard() {
        whenever(patchConfig.isActivated).thenReturn(true)

        val state = createViewModel().uiState.value

        // Connected + activated + not paused -> no top banner.
        assertThat(state.statusBanner).isNull()

        // Status row present and reads "Running" (patchState defaults keep basal/temp rows off).
        val statusRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Status" }
        assertThat(statusRow.value).isEqualTo("Running")

        // Activated -> primary action is Suspend, management offers Discard Patch.
        assertThat(state.primaryActions.map { it.label }).containsExactly("Suspend")
        assertThat(state.managementActions.map { it.label }).containsExactly("Discard Patch")
    }
}
