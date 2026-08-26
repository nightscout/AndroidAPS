package app.aaps.pump.carelevo.compose.patchflow

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectPrepareEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI test for [CarelevoPatchFlowStep02Connect] — the activation wizard's scan/connect step.
 *
 * **Injection strategy:** both ViewModels are Mockito mocks (Mockito 5's inline mock-maker handles the
 * final Kotlin classes, exactly as `CarelevoPatchConnectViewModelTest` in this module already relies on).
 * The composable only *reads* `viewModel.event` and `sharedViewModel.inputInsulin` and *calls* command
 * methods, so a mock covers the whole contract:
 *  - `viewModel.event` is stubbed with a **real** [MutableEventFlow] (`replay = 8`, so `emit` from the
 *    test thread never suspends waiting on the Compose-side collector), which lets every branch of the
 *    screen's `LaunchedEffect` event `when` be driven deterministically,
 *  - every command (`startScan`, `startConnect`, `startPatchDiscardProcess`) and the shared VM's
 *    `setPage` are asserted with `verify`.
 *
 * Labels come from resources rather than hardcoded English so the test survives label/locale changes.
 *
 * **Not covered:** the two dialogs' `onDismissRequest` lambdas (back-press / scrim tap) — Compose's test
 * API cannot dispatch a dialog dismissal, and both merely flip the same local flag the Cancel/Rescan
 * paths already exercise.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoPatchFlowStep02ConnectTest {

    @get:Rule
    val compose = createComposeRule()

    private val viewModel: CarelevoPatchConnectViewModel = mock()
    private val sharedViewModel: CarelevoPatchConnectionFlowViewModel = mock()

    /** Replay of 8 keeps [emitEvent] non-suspending; consumed slots are skipped by `EventFlowSlot`. */
    private val events = MutableEventFlow<Event>(replay = 8)

    private var exitFlowCount = 0

    private lateinit var searchLabel: String
    private lateinit var deactivateLabel: String
    private lateinit var confirmLabel: String
    private lateinit var cancelLabel: String
    private lateinit var rescanLabel: String
    private lateinit var step1Label: String
    private lateinit var step2Label: String
    private lateinit var step1Title: String
    private lateinit var step1Desc: String
    private lateinit var step2Title: String
    private lateinit var step2Desc: String
    private lateinit var discardDialogTitle: String
    private lateinit var discardDialogDesc: String
    private lateinit var connectDialogTitle: String
    private lateinit var scanFailedMsg: String
    private lateinit var bluetoothNotEnabledMsg: String
    private lateinit var profileNotSetMsg: String
    private lateinit var patchNotFoundMsg: String
    private lateinit var connectFailedMsg: String
    private lateinit var discardFailedMsg: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        searchLabel = context.getString(R.string.carelevo_btn_input_search_patch)
        deactivateLabel = context.getString(R.string.carelevo_btn_patch_expiration)
        confirmLabel = context.getString(R.string.carelevo_btn_confirm)
        cancelLabel = context.getString(R.string.carelevo_btn_cancel)
        rescanLabel = context.getString(R.string.carelevo_btn_research)
        step1Label = context.getString(R.string.carelevo_patch_step_1)
        step2Label = context.getString(R.string.carelevo_patch_step_2)
        step1Title = context.getString(R.string.carelevo_patch_connect_step_1_title)
        step1Desc = context.getString(R.string.carelevo_patch_connect_step_1_desc)
        step2Title = context.getString(R.string.carelevo_patch_connect_step_2_title)
        step2Desc = context.getString(R.string.carelevo_patch_connect_step_2_desc)
        discardDialogTitle = context.getString(R.string.carelevo_dialog_patch_discard_message_title)
        discardDialogDesc = context.getString(R.string.carelevo_dialog_patch_discard_message_desc)
        connectDialogTitle = context.getString(R.string.carelevo_dialog_patch_connect_message_title)
        scanFailedMsg = context.getString(R.string.carelevo_toast_msg_scan_failed)
        bluetoothNotEnabledMsg = context.getString(R.string.carelevo_toast_msg_bluetooth_not_enabled)
        profileNotSetMsg = context.getString(R.string.carelevo_toast_msg_profile_not_set)
        patchNotFoundMsg = context.getString(R.string.carelevo_toast_msg_patch_not_found)
        connectFailedMsg = context.getString(R.string.carelevo_toast_msg_connect_failed)
        discardFailedMsg = context.getString(R.string.carelevo_toast_msg_discard_failed)

        whenever(viewModel.event).thenReturn(events)
        whenever(sharedViewModel.inputInsulin).thenReturn(INPUT_INSULIN)
    }

    private fun setContent() {
        compose.setContent {
            MaterialTheme {
                CarelevoPatchFlowStep02Connect(
                    viewModel = viewModel,
                    sharedViewModel = sharedViewModel,
                    onExitFlow = { exitFlowCount++ }
                )
            }
        }
    }

    /** Push one event through the screen's collector and let the resulting recomposition settle. */
    private fun emitEvent(event: Event) {
        runBlocking { events.emit(event) }
        compose.waitForIdle()
    }

    private fun assertNoErrorBanner() {
        compose.onNodeWithText(scanFailedMsg).assertDoesNotExist()
        compose.onNodeWithText(bluetoothNotEnabledMsg).assertDoesNotExist()
        compose.onNodeWithText(profileNotSetMsg).assertDoesNotExist()
        compose.onNodeWithText(patchNotFoundMsg).assertDoesNotExist()
        compose.onNodeWithText(connectFailedMsg).assertDoesNotExist()
        compose.onNodeWithText(discardFailedMsg).assertDoesNotExist()
    }

    // region content

    @Test
    fun rendersBothStepSections_andNoErrorBanner_initially() {
        setContent()

        compose.onNodeWithText(step1Label).assertIsDisplayed()
        compose.onNodeWithText(step1Title).assertIsDisplayed()
        compose.onNodeWithText(step1Desc).assertIsDisplayed()
        compose.onNodeWithText(step2Label).assertIsDisplayed()
        compose.onNodeWithText(step2Title).assertIsDisplayed()
        compose.onNodeWithText(step2Desc).assertIsDisplayed()
        assertNoErrorBanner()
    }

    @Test
    fun rendersBothWizardButtons_enabled_andNoDialog() {
        setContent()

        compose.onNodeWithText(searchLabel).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(deactivateLabel).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(discardDialogTitle).assertDoesNotExist()
        compose.onNodeWithText(connectDialogTitle).assertDoesNotExist()
    }

    // endregion

    // region primary / secondary buttons

    @Test
    fun searchButton_click_callsStartScan() {
        setContent()

        compose.onNodeWithText(searchLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startScan()
        verify(viewModel, never()).startPatchDiscardProcess()
    }

    @Test
    fun searchButton_click_clearsPreviousErrorBanner() {
        setContent()
        emitEvent(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        compose.onNodeWithText(scanFailedMsg).assertIsDisplayed()

        compose.onNodeWithText(searchLabel).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(scanFailedMsg).assertDoesNotExist()
        verify(viewModel).startScan()
    }

    @Test
    fun deactivateButton_click_showsDiscardDialog_withoutCallingViewModel() {
        setContent()

        compose.onNodeWithText(deactivateLabel).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(discardDialogTitle).assertIsDisplayed()
        compose.onNodeWithText(discardDialogDesc).assertIsDisplayed()
        compose.onNodeWithText(confirmLabel).assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
        verify(viewModel, never()).startPatchDiscardProcess()
    }

    // endregion

    // region discard dialog

    @Test
    fun discardDialog_confirm_callsStartPatchDiscardProcess_andDismisses() {
        setContent()
        compose.onNodeWithText(deactivateLabel).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(confirmLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startPatchDiscardProcess()
        compose.onNodeWithText(discardDialogTitle).assertDoesNotExist()
        assertThat(exitFlowCount).isEqualTo(0)
    }

    @Test
    fun discardDialog_cancel_dismisses_withoutCallingViewModel() {
        setContent()
        compose.onNodeWithText(deactivateLabel).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(cancelLabel).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(discardDialogTitle).assertDoesNotExist()
        compose.onNodeWithText(searchLabel).assertIsDisplayed()
        verify(viewModel, never()).startPatchDiscardProcess()
        assertThat(exitFlowCount).isEqualTo(0)
    }

    // endregion

    // region connect dialog

    @Test
    fun showConnectDialogEvent_showsConnectDialog() {
        setContent()

        emitEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)

        compose.onNodeWithText(connectDialogTitle).assertIsDisplayed()
        compose.onNodeWithText(DEVICE_NAME).assertIsDisplayed()
        compose.onNodeWithText(confirmLabel).assertIsDisplayed()
        compose.onNodeWithText(rescanLabel).assertIsDisplayed()
    }

    @Test
    fun connectDialog_confirm_callsStartConnectWithInputInsulin_andDismisses() {
        setContent()
        emitEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)

        compose.onNodeWithText(confirmLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startConnect(INPUT_INSULIN)
        compose.onNodeWithText(connectDialogTitle).assertDoesNotExist()
        verify(viewModel, never()).startScan()
    }

    @Test
    fun connectDialog_rescan_callsStartScan_andDismisses() {
        setContent()
        emitEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)

        compose.onNodeWithText(rescanLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).startScan()
        compose.onNodeWithText(connectDialogTitle).assertDoesNotExist()
        verify(viewModel, never()).startConnect(INPUT_INSULIN)
    }

    // endregion

    // region error-banner events

    @Test
    fun scanFailedEvent_showsErrorBanner() {
        setContent()

        emitEvent(CarelevoConnectPrepareEvent.ShowMessageScanFailed)

        compose.onNodeWithText(scanFailedMsg).assertIsDisplayed()
        compose.onNodeWithText(step1Title).assertIsDisplayed()
    }

    @Test
    fun bluetoothNotEnabledEvent_showsErrorBanner() {
        setContent()

        emitEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)

        compose.onNodeWithText(bluetoothNotEnabledMsg).assertIsDisplayed()
    }

    @Test
    fun notSetUserSettingInfoEvent_showsProfileNotSetBanner() {
        setContent()

        emitEvent(CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo)

        compose.onNodeWithText(profileNotSetMsg).assertIsDisplayed()
    }

    @Test
    fun selectedDeviceIsEmptyEvent_showsPatchNotFoundBanner() {
        setContent()

        emitEvent(CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty)

        compose.onNodeWithText(patchNotFoundMsg).assertIsDisplayed()
    }

    @Test
    fun scanIsWorkingEvent_isIgnored() {
        setContent()

        emitEvent(CarelevoConnectPrepareEvent.ShowMessageScanIsWorking)

        assertNoErrorBanner()
        compose.onNodeWithText(connectDialogTitle).assertDoesNotExist()
        compose.onNodeWithText(searchLabel).assertIsDisplayed()
    }

    @Test
    fun noActionEvent_isIgnored() {
        setContent()

        emitEvent(CarelevoConnectPrepareEvent.NoAction)

        assertNoErrorBanner()
        compose.onNodeWithText(connectDialogTitle).assertDoesNotExist()
        compose.onNodeWithText(searchLabel).assertIsDisplayed()
    }

    @Test
    fun laterErrorEvent_replacesPreviousBanner() {
        setContent()
        emitEvent(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        compose.onNodeWithText(scanFailedMsg).assertIsDisplayed()

        emitEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)

        compose.onNodeWithText(bluetoothNotEnabledMsg).assertIsDisplayed()
        compose.onNodeWithText(scanFailedMsg).assertDoesNotExist()
    }

    // endregion

    // region terminal events

    @Test
    fun connectFailedEvent_dismissesConnectDialog_andShowsErrorBanner() {
        setContent()
        emitEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)
        compose.onNodeWithText(connectDialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectPrepareEvent.ConnectFailed)

        compose.onNodeWithText(connectDialogTitle).assertDoesNotExist()
        compose.onNodeWithText(connectFailedMsg).assertIsDisplayed()
        verify(sharedViewModel, never()).setPage(CarelevoPatchStep.SAFETY_CHECK)
    }

    @Test
    fun connectCompleteEvent_dismissesDialog_clearsError_andAdvancesToSafetyCheck() {
        setContent()
        emitEvent(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        emitEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)
        compose.onNodeWithText(connectDialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectPrepareEvent.ConnectComplete)

        compose.onNodeWithText(connectDialogTitle).assertDoesNotExist()
        assertNoErrorBanner()
        verify(sharedViewModel).setPage(CarelevoPatchStep.SAFETY_CHECK)
        assertThat(exitFlowCount).isEqualTo(0)
    }

    @Test
    fun discardCompleteEvent_dismissesDiscardDialog_andExitsFlow() {
        setContent()
        compose.onNodeWithText(deactivateLabel).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(discardDialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectPrepareEvent.DiscardComplete)

        compose.onNodeWithText(discardDialogTitle).assertDoesNotExist()
        assertThat(exitFlowCount).isEqualTo(1)
        assertNoErrorBanner()
    }

    @Test
    fun discardFailedEvent_dismissesDiscardDialog_andShowsErrorBanner() {
        setContent()
        compose.onNodeWithText(deactivateLabel).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(discardDialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectPrepareEvent.DiscardFailed)

        compose.onNodeWithText(discardDialogTitle).assertDoesNotExist()
        compose.onNodeWithText(discardFailedMsg).assertIsDisplayed()
        assertThat(exitFlowCount).isEqualTo(0)
    }

    // endregion

    private companion object {

        private const val INPUT_INSULIN = 250

        /** Hardcoded in the connect dialog's `content` — the pump brand, deliberately not localized. */
        private const val DEVICE_NAME = "CareLevo"
    }
}
