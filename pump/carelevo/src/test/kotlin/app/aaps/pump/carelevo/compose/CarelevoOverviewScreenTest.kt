package app.aaps.pump.carelevo.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.State
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.presentation.model.CarelevoOverviewEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoOverviewViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * UI tests for [CarelevoOverviewScreen] — the Carelevo-specific glue around the shared
 * [app.aaps.core.ui.compose.pump.PumpOverviewScreen]: the one-shot event router (snackbars, the three
 * dialogs, the connection-flow hand-off) and the blocking progress overlay.
 *
 * The screen takes its [CarelevoOverviewViewModel] as an explicit parameter (no `hiltViewModel()`
 * default), so a Mockito mock backed by real `MutableStateFlow`s / a real `MutableEventFlow` drives
 * every branch without any of the pump's BLE collaborators. The rendering of the state itself
 * (banner/rows/buttons) is covered at its home in `:core:ui` by `PumpOverviewScreenTest`; here we
 * assert only that Carelevo hands its state through and reacts to events correctly.
 *
 * [MaterialTheme] is enough — the screen and its dialogs read `MaterialTheme.colorScheme` only, never
 * `AapsTheme` values.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone-sized viewport: the stop-duration dialog lays its 8 options out in a plain (non-scrolling)
// Column inside AlertDialog's text slot, so on Robolectric's small default screen the last options
// are clipped to zero height and can be neither asserted as displayed nor clicked.
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CarelevoOverviewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val overviewState = MutableStateFlow(PumpOverviewUiState())
    private val actionState = MutableStateFlow<State>(UiState.Idle)
    private val events = MutableEventFlow<Event>()
    private val snackbarHostState = SnackbarHostState()
    private val startedWorkflows = mutableListOf<CarelevoScreenType>()

    private val viewModel = mock<CarelevoOverviewViewModel>()

    // ── strings ────────────────────────────────────────────────────────────────
    private val confirm = context.getString(R.string.carelevo_btn_confirm)
    private val cancel = context.getString(R.string.carelevo_btn_cancel)
    private val loading = context.getString(CoreUiR.string.loading)
    private val discardTitle = context.getString(R.string.carelevo_dialog_patch_discard_message_title)
    private val discardDesc = context.getString(R.string.carelevo_dialog_patch_discard_message_desc)
    private val resumeTitle = context.getString(R.string.carelevo_pump_resume_title)
    private val resumeDesc = context.getString(R.string.carelevo_pump_resume_description)
    private val stopDurationTitle = context.getString(R.string.carelevo_pump_stop_duration_title)
    private val label30Min = context.getString(R.string.carelevo_pump_stop_duration_label_30_min)
    private val label4Hour = context.getString(R.string.carelevo_pump_stop_duration_label_4_hour)

    @Before
    fun setUp() {
        whenever(viewModel.overviewUiState).thenReturn(overviewState)
        whenever(viewModel.uiState).thenReturn(actionState)
        whenever(viewModel.event).thenReturn(events.asEventFlow())
    }

    // ── harness ────────────────────────────────────────────────────────────────

    /** Hosts the screen next to a [SnackbarHost] so event-driven messages are assertable as text. */
    private fun setContent() {
        compose.setContent {
            MaterialTheme {
                Box {
                    CarelevoOverviewScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onStartWorkflow = { startedWorkflows += it }
                    )
                    SnackbarHost(hostState = snackbarHostState)
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Emits one event and lets the screen's collector drain it. One event at a time: the underlying
     * shared flow has replay=1 / no extra buffer, so emitting twice without draining would suspend —
     * and Robolectric runs this test on the main thread that the collector needs.
     */
    private fun emit(event: CarelevoOverviewEvent) {
        runBlocking { events.emit(event) }
        compose.waitForIdle()
    }

    private fun stateWith(vararg actions: PumpAction) = PumpOverviewUiState(
        statusBanner = StatusBanner(text = "Connected", level = StatusLevel.NORMAL),
        managementActions = actions.toList()
    )

    // ── bootstrap (LaunchedEffect) ─────────────────────────────────────────────

    @Test
    fun firstComposition_startsObservers_andRefreshes_whenNotYetCreated() {
        whenever(viewModel.isCreated).thenReturn(false)

        setContent()

        verify(viewModel).observePatchInfo()
        verify(viewModel).observePatchState()
        verify(viewModel).observeInfusionInfo()
        verify(viewModel).observeProfile()
        verify(viewModel).setIsCreated(true)
        verify(viewModel).refreshPatchInfusionInfo()
    }

    @Test
    fun firstComposition_skipsObservers_butStillRefreshes_whenAlreadyCreated() {
        whenever(viewModel.isCreated).thenReturn(true)

        setContent()

        verify(viewModel, never()).observePatchInfo()
        verify(viewModel, never()).observePatchState()
        verify(viewModel, never()).observeInfusionInfo()
        verify(viewModel, never()).observeProfile()
        verify(viewModel, never()).setIsCreated(true)
        verify(viewModel).refreshPatchInfusionInfo()
    }

    // ── state pass-through ─────────────────────────────────────────────────────

    @Test
    fun rendersBannerAndInfoRows_fromViewModelState() {
        // Banner and row values are deliberately distinct strings: onNodeWithText fails on 2 matches.
        overviewState.value = PumpOverviewUiState(
            statusBanner = StatusBanner(text = "Patch connected", level = StatusLevel.NORMAL),
            queueStatus = "Reading status",
            infoRows = listOf(
                PumpInfoRow(label = context.getString(R.string.carelevo_bluetooth_state_key), value = "Connected"),
                PumpInfoRow(label = context.getString(R.string.carelevo_serial_number_key), value = "04:CD:15:D0:10:05")
            )
        )

        setContent()

        compose.onNodeWithText("Patch connected").assertIsDisplayed()
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("Reading status").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.carelevo_bluetooth_state_key)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.carelevo_serial_number_key)).assertIsDisplayed()
        compose.onNodeWithText("04:CD:15:D0:10:05").assertIsDisplayed()
    }

    @Test
    fun noActivePatch_showsWarningBannerAndConnectAction_butNoManagementActions() {
        val connectLabel = context.getString(R.string.carelevo_overview_connect_btn_label)
        overviewState.value = PumpOverviewUiState(
            statusBanner = StatusBanner(text = context.getString(R.string.carelevo_state_none_value), level = StatusLevel.WARNING),
            primaryActions = listOf(PumpAction(label = connectLabel, category = ActionCategory.PRIMARY, onClick = {}))
        )

        setContent()

        compose.onNodeWithText(context.getString(R.string.carelevo_state_none_value)).assertIsDisplayed()
        compose.onNodeWithText(connectLabel).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.carelevo_overview_pump_discard_btn_label)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(CoreUiR.string.pump_suspend)).assertDoesNotExist()
    }

    @Test
    fun recomposes_whenViewModelStateChanges() {
        overviewState.value = PumpOverviewUiState(statusBanner = StatusBanner(text = "No Active Patch", level = StatusLevel.WARNING))
        setContent()
        compose.onNodeWithText("No Active Patch").assertIsDisplayed()

        overviewState.value = PumpOverviewUiState(statusBanner = StatusBanner(text = "Connected", level = StatusLevel.NORMAL))
        compose.waitForIdle()

        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("No Active Patch").assertDoesNotExist()
    }

    @Test
    fun actionClick_invokesActionCallback() {
        var clicks = 0
        overviewState.value = stateWith(
            PumpAction(label = "Discard Patch", category = ActionCategory.MANAGEMENT, onClick = { clicks++ })
        )

        setContent()
        compose.onNodeWithText("Discard Patch").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun disabledAction_isRenderedButNotEnabled() {
        overviewState.value = stateWith(
            PumpAction(label = "Discard Patch", category = ActionCategory.MANAGEMENT, enabled = false, onClick = {})
        )

        setContent()

        compose.onNodeWithText("Discard Patch").assertIsDisplayed().assertIsNotEnabled()
    }

    // ── loading overlay ────────────────────────────────────────────────────────

    @Test
    fun loadingOverlay_hidden_whenActionStateIdle() {
        actionState.value = UiState.Idle

        setContent()

        compose.onNodeWithText(loading).assertDoesNotExist()
    }

    @Test
    fun loadingOverlay_shown_whenActionStateLoading() {
        actionState.value = UiState.Loading

        setContent()

        compose.onNodeWithText(loading).assertIsDisplayed()
    }

    @Test
    fun loadingOverlay_appearsAndDisappears_withActionState() {
        setContent()
        compose.onNodeWithText(loading).assertDoesNotExist()

        actionState.value = UiState.Loading
        compose.waitForIdle()
        compose.onNodeWithText(loading).assertIsDisplayed()

        actionState.value = UiState.Idle
        compose.waitForIdle()
        compose.onNodeWithText(loading).assertDoesNotExist()
    }

    @Test
    fun loadingOverlay_consumesTaps_soActionUnderneathCannotReFire() {
        var clicks = 0
        overviewState.value = stateWith(
            PumpAction(label = "Discard Patch", category = ActionCategory.MANAGEMENT, onClick = { clicks++ })
        )
        actionState.value = UiState.Loading

        setContent()
        compose.onNodeWithText("Discard Patch").performClick()
        compose.waitForIdle()

        assertThat(clicks).isEqualTo(0)
    }

    // ── event → snackbar ───────────────────────────────────────────────────────

    @Test
    fun event_showMessageBluetoothNotEnabled_showsSnackbar() {
        setContent()

        emit(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)

        compose.onNodeWithText(context.getString(R.string.carelevo_toast_msg_bluetooth_not_enabled)).assertIsDisplayed()
    }

    @Test
    fun event_carelevoIsNotConnected_showsSnackbar() {
        setContent()

        emit(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)

        compose.onNodeWithText(context.getString(R.string.carelevo_toast_msg_patch_not_connected)).assertIsDisplayed()
    }

    @Test
    fun event_discardFailed_showsSnackbar() {
        setContent()

        emit(CarelevoOverviewEvent.DiscardFailed)

        compose.onNodeWithText(context.getString(R.string.carelevo_toast_msg_discard_failed)).assertIsDisplayed()
    }

    @Test
    fun event_resumePumpFailed_showsSnackbar() {
        setContent()

        emit(CarelevoOverviewEvent.ResumePumpFailed)

        compose.onNodeWithText(context.getString(R.string.carelevo_toast_mag_set_basal_resume_fail)).assertIsDisplayed()
    }

    @Test
    fun event_stopPumpFailed_showsSnackbar() {
        setContent()

        emit(CarelevoOverviewEvent.StopPumpFailed)

        compose.onNodeWithText(context.getString(R.string.carelevo_toast_mag_set_basal_suspend_fail)).assertIsDisplayed()
    }

    // ── event → connection flow ────────────────────────────────────────────────

    @Test
    fun event_startConnectionFlow_hostsWorkflow() {
        setContent()

        emit(CarelevoOverviewEvent.StartConnectionFlow)

        assertThat(startedWorkflows).containsExactly(CarelevoScreenType.CONNECTION_FLOW_START)
    }

    @Test
    fun event_noAction_doesNothing() {
        setContent()

        emit(CarelevoOverviewEvent.NoAction)

        assertThat(startedWorkflows).isEmpty()
        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    @Test
    fun event_clickPumpStopResumeBtn_doesNothing_asItIsResolvedInTheViewModel() {
        setContent()

        emit(CarelevoOverviewEvent.ClickPumpStopResumeBtn)

        assertThat(startedWorkflows).isEmpty()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    // ── discard dialog ─────────────────────────────────────────────────────────

    @Test
    fun discardDialog_notShownInitially() {
        setContent()

        compose.onNodeWithText(discardTitle).assertDoesNotExist()
    }

    @Test
    fun event_showPumpDiscardDialog_showsDialog() {
        setContent()

        emit(CarelevoOverviewEvent.ShowPumpDiscardDialog)

        compose.onNodeWithText(discardTitle).assertIsDisplayed()
        compose.onNodeWithText(discardDesc).assertIsDisplayed()
        compose.onNodeWithText(confirm).assertIsDisplayed()
        compose.onNodeWithText(cancel).assertIsDisplayed()
    }

    @Test
    fun discardDialog_confirm_startsDiscardAndDismisses() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpDiscardDialog)

        compose.onNodeWithText(confirm).performClick()
        compose.waitForIdle()

        verify(viewModel).startDiscardProcess()
        compose.onNodeWithText(discardTitle).assertDoesNotExist()
    }

    @Test
    fun discardDialog_cancel_dismissesWithoutDiscarding() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpDiscardDialog)

        compose.onNodeWithText(cancel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).startDiscardProcess()
        compose.onNodeWithText(discardTitle).assertDoesNotExist()
    }

    // ── resume dialog ──────────────────────────────────────────────────────────

    @Test
    fun event_showPumpResumeDialog_showsDialog() {
        setContent()

        emit(CarelevoOverviewEvent.ShowPumpResumeDialog)

        compose.onNodeWithText(resumeTitle).assertIsDisplayed()
        compose.onNodeWithText(resumeDesc).assertIsDisplayed()
    }

    @Test
    fun resumeDialog_confirm_startsResumeAndDismisses() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpResumeDialog)

        compose.onNodeWithText(confirm).performClick()
        compose.waitForIdle()

        verify(viewModel).startPumpResume()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
    }

    @Test
    fun resumeDialog_cancel_dismissesWithoutResuming() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpResumeDialog)

        compose.onNodeWithText(cancel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).startPumpResume()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
    }

    // ── stop-duration dialog ───────────────────────────────────────────────────

    @Test
    fun event_showPumpStopDurationSelectDialog_showsAllDurationOptions() {
        setContent()

        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)

        compose.onNodeWithText(stopDurationTitle).assertIsDisplayed()
        listOf(
            R.string.carelevo_pump_stop_duration_label_30_min,
            R.string.carelevo_pump_stop_duration_label_1_hour,
            R.string.carelevo_pump_stop_duration_label_1_hour_30_min,
            R.string.carelevo_pump_stop_duration_label_2_hour,
            R.string.carelevo_pump_stop_duration_label_2_hour_30_min,
            R.string.carelevo_pump_stop_duration_label_3_hour,
            R.string.carelevo_pump_stop_duration_label_3_hour_30_min,
            R.string.carelevo_pump_stop_duration_label_4_hour
        ).forEach { compose.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        compose.onAllNodes(isSelectable()).assertCountEquals(8)
    }

    @Test
    fun stopDurationDialog_firstOptionSelectedByDefault() {
        setContent()

        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)

        compose.onAllNodes(isSelectable())[0].assertIsSelected()
        compose.onAllNodes(isSelectable())[7].assertIsNotSelected()
        compose.onNodeWithText(label30Min).assertIsDisplayed()
    }

    @Test
    fun stopDurationDialog_confirmWithDefault_stopsFor30Minutes() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)

        compose.onNodeWithText(confirm).performClick()
        compose.waitForIdle()

        verify(viewModel).startPumpStopProcess(30)
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    @Test
    fun stopDurationDialog_selectingLastOption_stopsFor240Minutes() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)

        compose.onAllNodes(isSelectable())[7].performClick()
        compose.waitForIdle()
        compose.onAllNodes(isSelectable())[7].assertIsSelected()
        compose.onAllNodes(isSelectable())[0].assertIsNotSelected()
        compose.onNodeWithText(label4Hour).assertIsDisplayed()

        compose.onNodeWithText(confirm).performClick()
        compose.waitForIdle()

        verify(viewModel).startPumpStopProcess(240)
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    @Test
    fun stopDurationDialog_selectingMiddleOption_stopsForMatchingDuration() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)

        // index 3 → "2 hours" → 120 minutes
        compose.onAllNodes(isSelectable())[3].performClick()
        compose.waitForIdle()

        compose.onNodeWithText(confirm).performClick()
        compose.waitForIdle()

        verify(viewModel).startPumpStopProcess(120)
    }

    @Test
    fun stopDurationDialog_cancel_dismissesWithoutStopping() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)

        compose.onNodeWithText(cancel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).startPumpStopProcess(any())
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    @Test
    fun stopDurationDialog_reopensWithSelectionResetToDefault_afterCancellingANonDefaultChoice() {
        setContent()
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
        compose.onAllNodes(isSelectable())[7].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(cancel).performClick()
        compose.waitForIdle()

        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)

        compose.onAllNodes(isSelectable())[0].assertIsSelected()
        compose.onAllNodes(isSelectable())[7].assertIsNotSelected()
    }
}
