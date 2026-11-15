package app.aaps.pump.omnipod.eros.ui.compose

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
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress
import app.aaps.pump.omnipod.eros.driver.definition.FirmwareVersion
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.omnipod.common.R as CommonR

/**
 * Unit test for [ErosOverviewViewModel] — the Eros Omnipod overview state assembly.
 *
 * Constructs the VM directly with mocked ctor deps and asserts the initial [PumpOverviewUiState]
 * produced by buildUiState() (used as the stateIn initialValue). The rendering itself is covered by
 * core:ui's PumpOverviewScreenTest (ErosOverviewScreen delegates to PumpOverviewScreen).
 *
 * The VM extends [androidx.lifecycle.ViewModel] and stateIn's on viewModelScope, so Dispatchers.setMain
 * is installed. It also owns a private Default scope for the omnipodRefresh flow (rxBus collectors).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ErosOverviewViewModelTest {

    @Mock private lateinit var rh: ResourceHelper
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

    // Concrete (final) collaborators — Mockito 5 inline mock maker handles final classes/methods.
    private val podStateManager: ErosPodStateManager = mock()
    private val omnipodErosPumpPlugin: OmnipodErosPumpPlugin = mock()
    private val omnipodManager: AapsOmnipodErosManager = mock()
    private val omnipodUtil: AapsOmnipodUtil = mock()
    private val omnipodAlertUtil: OmnipodAlertUtil = mock()
    private val rileyLinkServiceData: RileyLinkServiceData = mock()
    private val serviceTaskExecutor: ServiceTaskExecutor = mock()
    private val resetRileyLinkConfigurationTaskProvider: () -> ResetRileyLinkConfigurationTask = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // PumpCommunicationStatus + omnipodRefresh subscribe to these at construction.
        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventOmnipodErosPumpValuesChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventRileyLinkDeviceStatusChange::class)).thenReturn(emptyFlow())

        // buildInfoRows()/buildManagementActions() read these unconditionally at construction.
        whenever(rileyLinkServiceData.rileyLinkServiceState).thenReturn(RileyLinkServiceState.NotStarted)
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NONE)

        // Every info-row / action label goes through rh.gs(Int); unstubbed returns null -> PumpInfoRow(label=null) NPE.
        whenever(rh.gs(any<Int>())).thenReturn("-")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ErosOverviewViewModel(
        rh, podStateManager, omnipodErosPumpPlugin, omnipodManager, omnipodUtil,
        omnipodAlertUtil, rileyLinkServiceData, serviceTaskExecutor, commandQueue, rxBus,
        dateUtil, preferences, profileFunction, notificationManager, uiInteraction,
        config, aapsLogger, resetRileyLinkConfigurationTaskProvider, ch
    )

    @Test
    fun noPodState_hasNoPrimaryActions_butStillRendersInfoRowsAndManagement() {
        // podStateManager.hasPodState() defaults to false -> not-initialized info branch + empty primary actions.
        val state = createViewModel().uiState.value

        assertThat(state.statusBanner).isNull()
        assertThat(state.queueStatus).isNull()
        // buildPrimaryActions() short-circuits to emptyList() when there is no pod state.
        assertThat(state.primaryActions).isEmpty()
        // Eros always renders the RileyLink status row + placeholder pod rows + errors row.
        assertThat(state.infoRows).isNotEmpty()
        // Management actions (activate/pair/history/reset...) are always built.
        assertThat(state.managementActions).isNotEmpty()
    }

    @Test
    fun podStateNotInitialized_buildsPrimaryActions_andPodStatusIsCritical() {
        whenever(podStateManager.hasPodState()).thenReturn(true)
        // isPodInitialized() stays false -> stays in the not-initialized info branch (no DateTime/timezone reads),
        // but hasPodState()==true makes buildPrimaryActions() emit its action list.
        whenever(rh.gs(CommonR.string.omnipod_common_overview_pod_status)).thenReturn("Pod status")

        val state = createViewModel().uiState.value

        assertThat(state.primaryActions).isNotEmpty()
        assertThat(state.infoRows).isNotEmpty()
        // buildPodStatusLevel(): !isPodActivationCompleted -> CRITICAL
        val podStatusRow = state.infoRows.filterIsInstance<PumpInfoRow>().first { it.label == "Pod status" }
        assertThat(podStatusRow.level).isEqualTo(StatusLevel.CRITICAL)
    }

    /**
     * A pod that faults part way through activation (after pairing) can only be removed with "Deactivate":
     * when deactivation fails, the wizard offers "Discard". "Discard" itself is not shown for a paired pod,
     * so without "Deactivate" the pod could not be removed at all (#1217).
     */
    @Test
    fun pairedPodPartWayThroughActivation_offersDeactivate() {
        whenever(rileyLinkServiceData.rileyLinkServiceState).thenReturn(RileyLinkServiceState.PumpConnectorReady)
        whenever(podStateManager.hasPodState()).thenReturn(true)
        whenever(podStateManager.isPodInitialized).thenReturn(true)
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.PRIMING_COMPLETED)
        // Pairing sets these, and the info rows of a paired pod show them.
        whenever(podStateManager.lot).thenReturn(123)
        whenever(podStateManager.tid).thenReturn(456)
        whenever(podStateManager.pmVersion).thenReturn(FirmwareVersion(2, 7, 0))
        whenever(podStateManager.piVersion).thenReturn(FirmwareVersion(2, 7, 0))
        whenever(podStateManager.timeZone).thenReturn(DateTimeZone.getDefault())
        whenever(podStateManager.time).thenReturn(DateTime.now())
        whenever(dateUtil.dateAndTimeString(any())).thenReturn("12.10.2026 10:00")
        whenever(rh.gs(any<Int>(), anyVararg<Any>())).thenReturn("value")
        whenever(ch.insulinAmountString(any())).thenReturn("50 U")
        whenever(rh.gs(CommonR.string.omnipod_common_pod_management_button_deactivate_pod)).thenReturn("Deactivate pod")

        val state = createViewModel().uiState.value

        val deactivate = state.managementActions.first { it.label == "Deactivate pod" }
        assertThat(deactivate.visible).isTrue()
        assertThat(deactivate.enabled).isTrue()
    }

    @Test
    fun noPodState_hidesDeactivate() {
        whenever(rh.gs(CommonR.string.omnipod_common_pod_management_button_deactivate_pod)).thenReturn("Deactivate pod")

        val state = createViewModel().uiState.value

        val deactivate = state.managementActions.first { it.label == "Deactivate pod" }
        assertThat(deactivate.visible).isFalse()
    }
}
