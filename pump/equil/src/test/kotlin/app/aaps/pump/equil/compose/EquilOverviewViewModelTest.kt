package app.aaps.pump.equil.compose

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.events.EventEquilModeChanged
import app.aaps.pump.equil.manager.EquilManager
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
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR

/**
 * Unit test for the shared Equil overview state logic. [EquilOverviewViewModel] extends
 * androidx.lifecycle.ViewModel and uses viewModelScope, and assembles a PumpOverviewUiState via
 * buildUiState() (evaluated eagerly as the stateIn initial value at construction). This covers the
 * not-paired (empty) state and a configured/paired state exercising the info-row + status-banner
 * level logic. Rendering itself is covered by core:ui's PumpOverviewScreenTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EquilOverviewViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var context: Context

    private val equilPumpPlugin: EquilPumpPlugin = mock()
    private val equilManager: EquilManager = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // init { } subscribes to these via launchIn(viewModelScope), and PumpCommunicationStatus
        // subscribes to the two core events at construction. Unstubbed -> construction NPEs.
        whenever(rxBus.toFlow(EventEquilDataChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventEquilModeChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())

        // formatting collaborators used by buildPairedInfoRows / buildStatusBanner
        whenever(ch.insulinAmountString(any())).thenReturn("0 U")
        whenever(ch.basalRateString(any(), any(), any())).thenReturn("0.00 U/h")
        whenever(dateUtil.dateAndTimeAndSecondsString(any())).thenReturn("time")
        whenever(equilPumpPlugin.baseBasalRate).thenReturn(PumpRate(0.0))
        whenever(equilManager.isActivationCompleted()).thenReturn(true)

        // Every info-row / action label the VM builds. Unstubbed rh.gs -> null -> non-null String NPE.
        whenever(rh.gs(R.string.equil_pair)).thenReturn("Pair")
        whenever(rh.gs(R.string.equil_dressing)).thenReturn("Dressing")
        whenever(rh.gs(R.string.equil_unbind)).thenReturn("Unbind")
        whenever(rh.gs(CoreUiR.string.history)).thenReturn("History")
        whenever(rh.gs(R.string.equil_serialnumber)).thenReturn("Serial")
        whenever(rh.gs(R.string.equil_firmware_version)).thenReturn("Firmware")
        whenever(rh.gs(R.string.equil_mode)).thenReturn("Mode")
        whenever(rh.gs(R.string.equil_mode_running)).thenReturn("Running")
        whenever(rh.gs(R.string.equil_mode_suspended)).thenReturn("Suspended")
        whenever(rh.gs(R.string.equil_mode_stopped)).thenReturn("Stopped")
        whenever(rh.gs(R.string.equil_init_insulin_error)).thenReturn("Init error")
        whenever(rh.gs(R.string.equil_insulin_reservoir)).thenReturn("Reservoir")
        whenever(rh.gs(R.string.equil_basal_speed)).thenReturn("Basal speed")
        whenever(rh.gs(R.string.equil_total_delivered)).thenReturn("Total delivered")
        whenever(rh.gs(R.string.equil_common_overview_button_resume_delivery)).thenReturn("Resume")
        whenever(rh.gs(R.string.equil_common_overview_button_suspend_delivery)).thenReturn("Suspend")
        whenever(rh.gs(CoreUiR.string.last_connection_label)).thenReturn("Last connection")
        whenever(rh.gs(CoreUiR.string.battery_label)).thenReturn("Battery")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = EquilOverviewViewModel(
        rh, aapsLogger, dateUtil, ch, equilPumpPlugin, equilManager,
        commandQueue, preferences, rxBus, context
    )

    @Test
    fun nullEquilState_hasNoInfoRows_noPrimaryActions() {
        // isPaired = !TextUtils.isEmpty(devName); TextUtils.isEmpty returns false under the plain-JVM
        // mockable android.jar, so isPaired is effectively always true here — the not-paired branch
        // isn't reachable without Robolectric. With a null equilState the paired path yields no rows.
        whenever(equilManager.equilState).thenReturn(null)

        val state = createViewModel().uiState.value

        assertThat(state.infoRows).isEmpty()
        assertThat(state.statusBanner).isNull()
        assertThat(state.queueStatus).isNull()
        assertThat(state.primaryActions).isEmpty()
        assertThat(state.managementActions.map { it.label }).containsExactly("Dressing", "History", "Unbind")
    }

    @Test
    fun paired_suspended_buildsInfoRows_andWarningLevels() {
        val equilState = EquilManager.EquilState().apply {
            serialNumber = "SN123"
            firmwareVersion = "1.0"
            runMode = RunMode.SUSPEND
            lastDataTime = 0L
            battery = 40
            currentInsulin = 0
            startInsulin = -1        // -1 -> total delivered "-" (skips a ch call)
            tempBasal = null         // skips temp-basal row (isTempBasalRunning not called)
            bolusRecord = null       // skips last-bolus row
        }
        whenever(equilManager.equilState).thenReturn(equilState)

        val state = createViewModel().uiState.value

        assertThat(state.infoRows).isNotEmpty()
        val modeRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Mode" }
        assertThat(modeRow.value).isEqualTo("Suspended")
        assertThat(modeRow.level).isEqualTo(StatusLevel.WARNING)

        // paired + suspended -> WARNING status banner
        assertThat(state.statusBanner?.text).isEqualTo("Suspended")
        assertThat(state.statusBanner?.level).isEqualTo(StatusLevel.WARNING)

        // paired -> management offers Dressing / History / Unbind (not Pair)
        assertThat(state.managementActions.map { it.label }).containsExactly("Dressing", "History", "Unbind")
        // suspended -> primary action offers Resume
        assertThat(state.primaryActions.map { it.label }).containsExactly("Resume")
    }
}
