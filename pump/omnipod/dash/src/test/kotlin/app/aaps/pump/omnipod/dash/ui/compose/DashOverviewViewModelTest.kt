package app.aaps.pump.omnipod.dash.ui.compose

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.omnipod.common.EventOmnipodDashPumpValuesChanged
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.OmnipodDashPumpPlugin
import app.aaps.pump.omnipod.dash.R
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
import app.aaps.pump.omnipod.common.R as CommonR

/**
 * Unit test for [DashOverviewViewModel]'s [PumpOverviewUiState] assembly (the initial value produced
 * by buildUiState()). The screen rendering itself is covered by core:ui's PumpOverviewScreenTest.
 *
 * Both tests keep the pod in the pre-activation ("not initialized", activationProgress = NOT_STARTED)
 * state which is the least stub-heavy branch of buildInfoRows(): it emits placeholder rows and never
 * dereferences the pod's optional fields (basalProgram, lastBolus, time, expiry, ...) nor the pump
 * plugin's model()/determineCorrect* helpers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DashOverviewViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var podStateManager: OmnipodDashPodStateManager
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var uiInteraction: UiInteraction
    @Mock private lateinit var config: Config
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var context: Context

    private val omnipodDashPumpPlugin: OmnipodDashPumpPlugin = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // rx wiring touched at construction: PumpCommunicationStatus subscribes to these two, and the
        // omnipodRefresh field launches a collector on EventOmnipodDashPumpValuesChanged.
        whenever(rxBus.toFlow(EventPumpStatusChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class.java)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventOmnipodDashPumpValuesChanged::class.java)).thenReturn(emptyFlow())

        // buildUiState() reads activationProgress heavily; NOT_STARTED keeps the simple pre-activation branch.
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NOT_STARTED)

        // Every info-row / action label goes through rh.gs(Int); unstubbed returns null -> non-null String NPE.
        whenever(rh.gs(any<Int>())).thenReturn("label")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashOverviewViewModel(
        rh, podStateManager, omnipodDashPumpPlugin, commandQueue, rxBus, dateUtil,
        preferences, profileFunction, notificationManager, uiInteraction, config,
        aapsLogger, ch, context
    )

    @Test
    fun preActivation_buildsPlaceholderRows_criticalPodStatus_andOffersActivate() {
        whenever(rh.gs(CommonR.string.omnipod_common_overview_pod_status)).thenReturn("Pod status")
        whenever(rh.gs(CommonR.string.omnipod_common_pod_management_button_activate_pod)).thenReturn("Activate pod")
        whenever(rh.gs(CoreUiR.string.refresh)).thenReturn("Refresh")

        val state = createViewModel().uiState.value

        // Dash always renders the info section (unlike the Dana not-configured empty state).
        assertThat(state.infoRows).isNotEmpty()
        assertThat(state.statusBanner).isNull()
        assertThat(state.queueStatus).isNull()

        // Pre-activation pod is not activation-completed -> pod status row is CRITICAL.
        val podStatusRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Pod status" }
        assertThat(podStatusRow.level).isEqualTo(StatusLevel.CRITICAL)

        // Refresh needs a set unique id (false here) -> disabled; management offers "Activate pod".
        val refresh = state.primaryActions.first { it.label == "Refresh" }
        assertThat(refresh.enabled).isFalse()
        val activate = state.managementActions.first { it.label == "Activate pod" }
        assertThat(activate.visible).isTrue()
    }

    @Test
    fun poorConnectionQuality_marksBluetoothRowCritical() {
        // 50% success over >50 attempts -> connPct(50) < 70 && attempts > 50 -> CRITICAL.
        whenever(podStateManager.connectionSuccessRatio()).thenReturn(0.5f)
        whenever(podStateManager.successfulConnectionAttemptsAfterRetries).thenReturn(60)
        whenever(rh.gs(R.string.omnipod_dash_overview_bluetooth_connection_quality)).thenReturn("BT quality")

        val state = createViewModel().uiState.value

        val btQualityRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "BT quality" }
        assertThat(btQualityRow.level).isEqualTo(StatusLevel.CRITICAL)
    }
}
