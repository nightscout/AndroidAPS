package app.aaps.pump.omnipod.omnipod5.ui.compose

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.queue.command.CommandResumeDelivery
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.time.Duration
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.omnipod.common.R as CommonR

/**
 * Mirrors `DashOverviewViewModelTest`'s pattern for
 * O5's [O5OverviewViewModel] - covers [app.aaps.core.ui.compose.pump.PumpOverviewUiState]
 * assembly (the initial value produced by `buildUiState()`), including action visibility
 * before activation and while delivery is running or suspended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class O5OverviewViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var podStateManager: O5PodStateManager
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var config: Config
    @Mock private lateinit var ch: ConcentrationHelper
    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class)).thenReturn(emptyFlow())

        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NOT_STARTED)

        whenever(rh.gs(any<Int>())).thenReturn("label")
        whenever(rh.gs(any<Int>(), any(), any())).thenReturn("label")
        whenever(dateUtil.dateAndTimeString(any())).thenReturn("date")
        whenever(ch.insulinAmountString(any())).thenReturn("0 U")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = O5OverviewViewModel(rh, podStateManager, commandQueue, rxBus, dateUtil, config, ch)

    @Test
    fun preActivation_buildsPlaceholderRows_criticalPodStatus_andOffersActivate() {
        whenever(rh.gs(CommonR.string.omnipod_common_overview_pod_status)).thenReturn("Pod status")
        whenever(rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date)).thenReturn("Pod expires")
        whenever(rh.gs(CommonR.string.omnipod_common_overview_pod_hard_end_date)).thenReturn("Pod hard end")
        whenever(rh.gs(CommonR.string.omnipod_common_pod_management_button_activate_pod)).thenReturn("Activate pod")
        whenever(rh.gs(CoreUiR.string.refresh)).thenReturn("Refresh")

        val state = createViewModel().uiState.value

        assertThat(state.infoRows).isNotEmpty()
        assertThat(state.statusBanner).isNull()
        assertThat(state.queueStatus).isNull()

        val podStatusRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Pod status" }
        assertThat(podStatusRow.level).isEqualTo(StatusLevel.CRITICAL)

        val podExpiresIndex = state.infoRows.indexOfFirst { it is PumpInfoRow && it.label == "Pod expires" }
        val podHardEndRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Pod hard end" }
        assertThat(podHardEndRow.value).isEqualTo("-")
        assertThat(state.infoRows.indexOf(podHardEndRow)).isEqualTo(podExpiresIndex + 1)

        val refresh = state.primaryActions.first { it.label == "Refresh" }
        assertThat(refresh.enabled).isFalse()
        val activate = state.managementActions.first { it.label == "Activate pod" }
        assertThat(activate.visible).isTrue()
    }

    @Test
    fun activePod_hardEndIsEightHoursAfterExpiry() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.podLifeInHours).thenReturn(16.toShort())
        whenever(podStateManager.minutesSinceActivation).thenReturn(0.toShort())
        whenever(podStateManager.lastStatusResponseReceived).thenReturn(System.currentTimeMillis())
        whenever(rh.gs(CommonR.string.omnipod_common_overview_pod_expiry_date)).thenReturn("Pod expires")
        whenever(rh.gs(CommonR.string.omnipod_common_overview_pod_hard_end_date)).thenReturn("Pod hard end")
        whenever(dateUtil.dateAndTimeString(any())).thenAnswer { invocation -> invocation.getArgument<Long>(0).toString() }

        val rows = createViewModel().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()
        val expiryValue = rows.first { it.label == "Pod expires" }.value.toLong()
        val hardEndValue = rows.first { it.label == "Pod hard end" }.value.toLong()

        assertThat(hardEndValue - expiryValue).isEqualTo(Duration.ofHours(8).toMillis())
    }

    @Test
    fun noConnectionAttemptsYet_showsPlaceholderBluetoothQuality() {
        whenever(rh.gs(CommonR.string.omnipod_common_overview_bluetooth_connection_quality)).thenReturn("BT quality")
        whenever(podStateManager.connectionAttempts).thenReturn(0)

        val state = createViewModel().uiState.value

        val btQualityRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "BT quality" }
        assertThat(btQualityRow.value).isEqualTo("-")
    }

    @Test
    fun connectionQuality_reportsSuccessfulOverAttemptedRatio() {
        whenever(rh.gs(CommonR.string.omnipod_common_overview_bluetooth_connection_quality)).thenReturn("BT quality")
        whenever(podStateManager.connectionAttempts).thenReturn(10)
        whenever(podStateManager.successfulConnections).thenReturn(8)

        val state = createViewModel().uiState.value

        val btQualityRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "BT quality" }
        assertThat(btQualityRow.value).contains("8/10")
    }

    @Test
    fun activatedPod_offersDeactivateNotActivate() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(rh.gs(CommonR.string.omnipod_common_pod_management_button_activate_pod)).thenReturn("Activate pod")
        whenever(rh.gs(CommonR.string.omnipod_common_pod_management_button_deactivate_pod)).thenReturn("Deactivate pod")

        val state = createViewModel().uiState.value

        val activate = state.managementActions.first { it.label == "Activate pod" }
        assertThat(activate.visible).isFalse()
        val deactivate = state.managementActions.first { it.label == "Deactivate pod" }
        assertThat(deactivate.visible).isTrue()
    }

    @Test
    fun suspendedActivePod_offersResumeNotSuspend() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.podStatus).thenReturn(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        whenever(podStateManager.deliverySuspended).thenReturn(true)
        whenever(rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery)).thenReturn("Resume")
        whenever(rh.gs(CommonR.string.omnipod_common_overview_button_suspend_delivery)).thenReturn("Suspend")

        val state = createViewModel().uiState.value

        val resume = state.primaryActions.first { it.label == "Resume" }
        assertThat(resume.visible).isTrue()
        assertThat(resume.enabled).isTrue()
        assertThat(state.primaryActions.none { it.label == "Suspend" }).isTrue()
    }

    @Test
    fun runningActivePod_offersNeitherSuspendNorResume() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.podStatus).thenReturn(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        whenever(podStateManager.deliverySuspended).thenReturn(false)
        whenever(rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery)).thenReturn("Resume")
        whenever(rh.gs(CommonR.string.omnipod_common_overview_button_suspend_delivery)).thenReturn("Suspend")

        val state = createViewModel().uiState.value

        assertThat(state.primaryActions.first { it.label == "Resume" }.visible).isFalse()
        assertThat(state.primaryActions.none { it.label == "Suspend" }).isTrue()
    }

    @Test
    fun suspendedActivePod_busyQueue_disablesResume() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.podStatus).thenReturn(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        whenever(podStateManager.deliverySuspended).thenReturn(true)
        whenever(commandQueue.size()).thenReturn(1)
        whenever(rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery)).thenReturn("Resume")

        val resume = createViewModel().uiState.value.primaryActions.first { it.label == "Resume" }

        assertThat(resume.visible).isTrue()
        assertThat(resume.enabled).isFalse()
    }

    @Test
    fun activePod_resumeQueued_keepsResumeVisible() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.podStatus).thenReturn(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        whenever(podStateManager.deliverySuspended).thenReturn(false)
        whenever(commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class)).thenReturn(true)
        whenever(commandQueue.size()).thenReturn(1)
        whenever(rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery)).thenReturn("Resume")

        val resume = createViewModel().uiState.value.primaryActions.first { it.label == "Resume" }

        assertThat(resume.visible).isTrue()
        assertThat(resume.enabled).isFalse()
    }

    @Test
    fun preActivation_suspendedState_doesNotOfferResume() {
        whenever(podStateManager.deliverySuspended).thenReturn(true)
        whenever(rh.gs(CommonR.string.omnipod_common_overview_button_resume_delivery)).thenReturn("Resume")

        val resume = createViewModel().uiState.value.primaryActions.first { it.label == "Resume" }

        assertThat(resume.visible).isFalse()
    }

    @Test
    fun above50Reservoir_showsOver50RatherThanAnExactAmount() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.reservoirPulsesRemaining).thenReturn(null)
        whenever(rh.gs(CoreUiR.string.reservoir_label)).thenReturn("Reservoir")
        whenever(rh.gs(eq(CoreUiR.string.overview_reservoir_concentration_value_over), eq("0 U"))).thenReturn(">50 U")

        val row = createViewModel().uiState.value.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Reservoir" }

        assertThat(row.value).isEqualTo(">50 U")
        assertThat(row.level).isEqualTo(StatusLevel.NORMAL)
    }

    @Test
    fun measuredReservoir_showsMeasuredAmount() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        whenever(podStateManager.reservoirPulsesRemaining).thenReturn(250)
        whenever(rh.gs(CoreUiR.string.reservoir_label)).thenReturn("Reservoir")
        whenever(ch.insulinAmountString(any())).thenReturn("12.5 U")
        whenever(ch.fromPump(any<PumpInsulin>(), eq(false))).thenReturn(12.5)

        val row = createViewModel().uiState.value.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Reservoir" }

        assertThat(row.value).isEqualTo("12.5 U")
        assertThat(row.level).isEqualTo(StatusLevel.CRITICAL)
    }
}
