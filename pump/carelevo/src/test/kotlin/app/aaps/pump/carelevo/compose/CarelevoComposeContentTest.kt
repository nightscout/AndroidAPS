package app.aaps.pump.carelevo.compose

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
// Aliased: the simple name collides with Robolectric's @Config annotation used below.
import app.aaps.core.interfaces.configuration.Config as AapsConfig
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.CarelevoAlarmNotifier
import app.aaps.pump.carelevo.common.MutableEventFlow
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * Tests for [CarelevoComposeContent] — the module's `ComposablePluginContent` entry point.
 *
 * **Scope caveat (read before extending this file).** `CarelevoComposeContent.Render` cannot be
 * rendered by a plain Robolectric Compose test: it resolves its ViewModel with a bare
 * `hiltViewModel()` call in the composable body (no parameter to override), and so do the children
 * it routes to (`CarelevoAlarmHost`, and four more inside `CarelevoPatchFlowScreen`). Since
 * androidx.hilt 1.4 `hiltViewModel()` unconditionally builds a `HiltViewModelFactory` from
 * `LocalContext`, which requires an `@AndroidEntryPoint` activity and throws otherwise — there is no
 * injection seam, and no amount of `LocalViewModelStoreOwner` seeding avoids it. Covering `Render`
 * itself needs a Hilt Robolectric harness (hilt-android-testing + kspTest + `HiltTestApplication` +
 * a Hilt test activity), which this module does not have.
 *
 * What is covered here instead:
 * - Construction of the entry point (the only non-composable code in the class).
 * - The one route whose content *is* injectable: the `activeWorkflowScreen == null` branch, i.e.
 *   [CarelevoOverviewScreen], which takes its ViewModel as an explicit parameter. This includes the
 *   `onStartWorkflow(CONNECTION_FLOW_START)` handshake that `Render`'s `startWorkflow` consumes.
 *
 * Not covered: the `CONNECTION_FLOW_START` and `else` routes (both delegate to
 * `CarelevoPatchFlowScreen`, which hard-calls `hiltViewModel()` four times), and `CarelevoAlarmHost`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoComposeContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val aapsLogger = mock<AAPSLogger>()
    private val carelevoAlarmNotifier = mock<CarelevoAlarmNotifier>()
    private val protectionCheck = mock<ProtectionCheck>()
    private val blePreCheck = mock<BlePreCheck>()
    private val iconsProvider = mock<IconsProvider>()

    /** Not emulating by default — the emulator path skips the BLE pre-check. */
    private val config = mock<AapsConfig> {
        on { isEnabled(ExternalOptions.EMULATE_CARELEVO) } doReturn false
    }

    /** Mocked so the screen's snackbar calls are verifiable without depending on snackbar animation/timing. */
    private val snackbarHostState: SnackbarHostState = mock {
        onBlocking { showSnackbar(any(), anyOrNull(), any(), any()) } doReturn SnackbarResult.Dismissed
    }

    private val events = MutableEventFlow<Event>()
    private val overviewState = MutableStateFlow(PumpOverviewUiState())
    private val actionState = MutableStateFlow<State>(UiState.Idle)
    private val startedWorkflows = mutableListOf<CarelevoScreenType>()

    private lateinit var viewModel: CarelevoOverviewViewModel
    private lateinit var context: Context

    private val confirmLabel get() = context.getString(R.string.carelevo_btn_confirm)
    private val cancelLabel get() = context.getString(R.string.carelevo_btn_cancel)
    private val discardTitle get() = context.getString(R.string.carelevo_dialog_patch_discard_message_title)
    private val resumeTitle get() = context.getString(R.string.carelevo_pump_resume_title)
    private val stopDurationTitle get() = context.getString(R.string.carelevo_pump_stop_duration_title)
    private val loadingLabel get() = context.getString(CoreUiR.string.loading)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        viewModel = mock()
        whenever(viewModel.overviewUiState).thenReturn(overviewState)
        whenever(viewModel.uiState).thenReturn(actionState)
        whenever(viewModel.event).thenReturn(events)
        whenever(viewModel.isCreated).thenReturn(false)
    }

    private fun buildContent(): ComposablePluginContent = CarelevoComposeContent(
        aapsLogger = aapsLogger,
        carelevoAlarmNotifier = carelevoAlarmNotifier,
        protectionCheck = protectionCheck,
        blePreCheck = blePreCheck,
        iconsProvider = iconsProvider,
        config = config
    )

    /** Renders the overview route exactly as `Render` composes it for `activeWorkflowScreen == null`. */
    private fun renderOverviewRoute() {
        compose.setContent {
            MaterialTheme {
                Box {
                    CarelevoOverviewScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onStartWorkflow = { startedWorkflows += it }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun emit(event: CarelevoOverviewEvent) = runBlocking { events.emit(event) }

    private fun assertSnackbarShown(expected: String) =
        verifyBlocking(snackbarHostState) { showSnackbar(eq(expected), anyOrNull(), any(), any()) }

    // ── Entry point construction ───────────────────────────────────────────────

    @Test
    fun construction_buildsComposablePluginContent_withoutTouchingCollaborators() {
        val content = buildContent()

        assertThat(content).isInstanceOf(CarelevoComposeContent::class.java)
        // The plugin builds this eagerly; construction must stay side-effect free.
        verifyNoInteractions(aapsLogger, carelevoAlarmNotifier, protectionCheck, blePreCheck, iconsProvider)
    }

    // ── Overview route: content ────────────────────────────────────────────────

    @Test
    fun overviewRoute_rendersBanner_queueStatus_infoRows_andActions() {
        overviewState.value = PumpOverviewUiState(
            statusBanner = StatusBanner(text = "Patch connected", level = StatusLevel.NORMAL),
            queueStatus = "Reading status",
            infoRows = listOf(
                PumpInfoRow(label = "Serial number", value = "04:CD:15:D0:10:05"),
                PumpInfoRow(label = "Reservoir", value = "298.0 U", level = StatusLevel.WARNING),
                PumpInfoRow(label = "Hidden row", value = "hidden value", visible = false)
            ),
            primaryActions = listOf(PumpAction(label = "Connect", onClick = {})),
            managementActions = listOf(PumpAction(label = "Discard", onClick = {}))
        )

        renderOverviewRoute()

        compose.onNodeWithText("Patch connected").assertIsDisplayed()
        compose.onNodeWithText("Reading status").assertIsDisplayed()
        compose.onNodeWithText("Serial number").assertIsDisplayed()
        compose.onNodeWithText("04:CD:15:D0:10:05").assertIsDisplayed()
        compose.onNodeWithText("Reservoir").assertIsDisplayed()
        compose.onNodeWithText("298.0 U").assertIsDisplayed()
        compose.onNodeWithText("hidden value").assertDoesNotExist()
        compose.onNodeWithText("Connect").assertIsDisplayed()
        compose.onNodeWithText("Discard").assertIsDisplayed()
    }

    @Test
    fun overviewRoute_showsNoDialogsOrOverlay_byDefault() {
        renderOverviewRoute()

        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
        compose.onNodeWithText(loadingLabel).assertDoesNotExist()
    }

    // ── Overview route: one-time init latch ────────────────────────────────────

    @Test
    fun overviewRoute_runsObserversOnce_whenViewModelNotYetCreated() {
        renderOverviewRoute()

        verify(viewModel).observePatchInfo()
        verify(viewModel).observePatchState()
        verify(viewModel).observeInfusionInfo()
        verify(viewModel).observeProfile()
        verify(viewModel).setIsCreated(true)
        verify(viewModel).refreshPatchInfusionInfo()
    }

    @Test
    fun overviewRoute_skipsObservers_whenViewModelAlreadyCreated() {
        whenever(viewModel.isCreated).thenReturn(true)

        renderOverviewRoute()

        verify(viewModel, never()).observePatchInfo()
        verify(viewModel, never()).observePatchState()
        verify(viewModel, never()).observeInfusionInfo()
        verify(viewModel, never()).observeProfile()
        verify(viewModel, never()).setIsCreated(any())
        // The status refresh is unconditional — it must still run on re-entry.
        verify(viewModel).refreshPatchInfusionInfo()
    }

    // ── Overview route: blocking loading overlay ───────────────────────────────

    @Test
    fun overviewRoute_showsLoadingOverlay_whenActionStateIsLoading() {
        // CircularProgressIndicator animates forever — the clock must not auto-advance or the rule
        // would never see an idle frame.
        compose.mainClock.autoAdvance = false
        actionState.value = UiState.Loading

        compose.setContent {
            MaterialTheme {
                Box {
                    CarelevoOverviewScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onStartWorkflow = { startedWorkflows += it }
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText(loadingLabel).assertIsDisplayed()
    }

    // ── Overview route → Render.startWorkflow handshake ────────────────────────

    @Test
    fun startConnectionFlowEvent_isForwardedAsConnectionFlowStart() {
        emit(CarelevoOverviewEvent.StartConnectionFlow)

        renderOverviewRoute()

        assertThat(startedWorkflows).containsExactly(CarelevoScreenType.CONNECTION_FLOW_START)
    }

    @Test
    fun noActionEvent_startsNoWorkflow_andShowsNoDialog() {
        emit(CarelevoOverviewEvent.NoAction)

        renderOverviewRoute()

        assertThat(startedWorkflows).isEmpty()
        compose.onNodeWithText(discardTitle).assertDoesNotExist()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    @Test
    fun clickPumpStopResumeBtnEvent_isConsumedWithoutUiChange() {
        emit(CarelevoOverviewEvent.ClickPumpStopResumeBtn)

        renderOverviewRoute()

        // Resolution happens in the VM; the screen must not open a dialog on this event.
        assertThat(startedWorkflows).isEmpty()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    // ── Overview route: discard dialog ─────────────────────────────────────────

    @Test
    fun discardDialog_confirm_startsDiscardProcess_andCloses() {
        emit(CarelevoOverviewEvent.ShowPumpDiscardDialog)
        renderOverviewRoute()

        compose.onNodeWithText(discardTitle).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.carelevo_dialog_patch_discard_message_desc)).assertIsDisplayed()

        compose.onNodeWithText(confirmLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startDiscardProcess()
        compose.onNodeWithText(discardTitle).assertDoesNotExist()
    }

    @Test
    fun discardDialog_cancel_closesWithoutDiscarding() {
        emit(CarelevoOverviewEvent.ShowPumpDiscardDialog)
        renderOverviewRoute()

        compose.onNodeWithText(cancelLabel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).startDiscardProcess()
        compose.onNodeWithText(discardTitle).assertDoesNotExist()
    }

    // ── Overview route: resume dialog ──────────────────────────────────────────

    @Test
    fun resumeDialog_confirm_startsPumpResume_andCloses() {
        emit(CarelevoOverviewEvent.ShowPumpResumeDialog)
        renderOverviewRoute()

        compose.onNodeWithText(resumeTitle).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.carelevo_pump_resume_description)).assertIsDisplayed()

        compose.onNodeWithText(confirmLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startPumpResume()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
    }

    @Test
    fun resumeDialog_cancel_closesWithoutResuming() {
        emit(CarelevoOverviewEvent.ShowPumpResumeDialog)
        renderOverviewRoute()

        compose.onNodeWithText(cancelLabel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).startPumpResume()
        compose.onNodeWithText(resumeTitle).assertDoesNotExist()
    }

    // ── Overview route: stop-duration dialog ───────────────────────────────────

    @Test
    fun stopDurationDialog_confirm_usesFirstDurationByDefault() {
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
        renderOverviewRoute()

        compose.onNodeWithText(stopDurationTitle).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.carelevo_pump_stop_duration_label_30_min)).assertIsDisplayed()

        compose.onNodeWithText(confirmLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startPumpStopProcess(30)
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    @Test
    fun stopDurationDialog_confirm_usesSelectedDuration() {
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
        renderOverviewRoute()

        // Options are ordered 30/60/90/… minutes — index 1 is "1 hour".
        compose.onAllNodes(isSelectable())[1].performClick()
        compose.waitForIdle()
        compose.onAllNodes(isSelectable())[1].assertIsSelected()

        compose.onNodeWithText(confirmLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startPumpStopProcess(60)
        verify(viewModel, never()).startPumpStopProcess(30)
    }

    @Test
    fun stopDurationDialog_cancel_closesWithoutStopping() {
        emit(CarelevoOverviewEvent.ShowPumpStopDurationSelectDialog)
        renderOverviewRoute()

        compose.onNodeWithText(cancelLabel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).startPumpStopProcess(any())
        compose.onNodeWithText(stopDurationTitle).assertDoesNotExist()
    }

    // ── Overview route: snackbar events ────────────────────────────────────────

    @Test
    fun bluetoothNotEnabledEvent_showsSnackbar() {
        emit(CarelevoOverviewEvent.ShowMessageBluetoothNotEnabled)
        renderOverviewRoute()

        assertSnackbarShown(context.getString(R.string.carelevo_toast_msg_bluetooth_not_enabled))
    }

    @Test
    fun notConnectedEvent_showsSnackbar() {
        emit(CarelevoOverviewEvent.ShowMessageCarelevoIsNotConnected)
        renderOverviewRoute()

        assertSnackbarShown(context.getString(R.string.carelevo_toast_msg_patch_not_connected))
    }

    @Test
    fun discardFailedEvent_showsSnackbar() {
        emit(CarelevoOverviewEvent.DiscardFailed)
        renderOverviewRoute()

        assertSnackbarShown(context.getString(R.string.carelevo_toast_msg_discard_failed))
    }

    @Test
    fun resumePumpFailedEvent_showsSnackbar() {
        emit(CarelevoOverviewEvent.ResumePumpFailed)
        renderOverviewRoute()

        assertSnackbarShown(context.getString(R.string.carelevo_toast_mag_set_basal_resume_fail))
    }

    @Test
    fun stopPumpFailedEvent_showsSnackbar() {
        emit(CarelevoOverviewEvent.StopPumpFailed)
        renderOverviewRoute()

        assertSnackbarShown(context.getString(R.string.carelevo_toast_mag_set_basal_suspend_fail))
    }
}
