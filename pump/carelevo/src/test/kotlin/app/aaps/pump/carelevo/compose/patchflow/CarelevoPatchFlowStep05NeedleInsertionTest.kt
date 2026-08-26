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
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectNeedleEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchNeedleInsertionViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
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
 * UI test for [CarelevoPatchFlowStep05NeedleInsertion] - the needle-insertion step of the Carelevo
 * patch activation wizard.
 *
 * The step is stateful: it renders one of two mutually exclusive layouts driven by the VM's
 * `isNeedleInsert` StateFlow (insertion instructions vs. detach-applicator guidance), and it derives
 * two further pieces of local state - `failCount` and `errorMessage` - purely from the VM's event
 * flow. All three inputs are therefore driven here through a mocked
 * [CarelevoPatchNeedleInsertionViewModel]:
 *  - `isNeedleInsert` is stubbed with a real [MutableStateFlow], seeded *before* `setContent` so
 *    `collectAsStateWithLifecycle` picks it up as the initial value (no lifecycle pumping needed),
 *  - `event` is stubbed with the driver's own [MutableEventFlow], created with a replay buffer large
 *    enough that `emit` never suspends (so [emitEvent] cannot deadlock the Robolectric main thread),
 *  - the imperative calls (`observePatchInfo`, `setIsCreated`, `startCheckNeedle`, `startSetBasal`,
 *    `startDiscardProcess`) are asserted with Mockito `verify`.
 *
 * Mocking the VM directly (rather than constructing the real one from mocked collaborators) keeps
 * RxJava, the CommandQueue and the pump-sync bookkeeping entirely out of this test - the VM's own
 * logic is not under test here, only the composable's reaction to it. The class is final Kotlin, but
 * Mockito 5's inline mock maker (the default) handles that.
 *
 * Wrapped in [MaterialTheme] only: the step and everything it delegates to (`WizardStepLayout`,
 * `ErrorBanner`, `CarelevoActionDialog`) read `MaterialTheme.colorScheme` / `typography` but no
 * `AapsTheme`-specific values - this mirrors the file's own `@Preview`s. Spacing comes from
 * `AapsSpacing`, a plain object needing no theme.
 *
 * A phone-sized qualifier is forced (matching `CarelevoAlarmScreenTest`) so the tall step content
 * (banner + two instruction sections + warning row + retry counter + pinned buttons) fits on screen
 * and `assertIsDisplayed` / `performClick` stay meaningful.
 *
 * Text is resolved from resources rather than hardcoded so the test survives label/locale changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class CarelevoPatchFlowStep05NeedleInsertionTest {

    @get:Rule
    val compose = createComposeRule()

    private val viewModel = mock<CarelevoPatchNeedleInsertionViewModel>()

    /** Backs the mocked `isNeedleInsert`; seed before [setScreen]. */
    private val needleInsertedFlow = MutableStateFlow(false)

    /**
     * Backs the mocked `event`. Replay is deliberately larger than the production default (1) so a
     * test can queue several events without `emit` ever suspending on a slow collector.
     */
    private val eventFlow = MutableEventFlow<Event>(replay = 16)

    private var exitFlowCalls = 0

    // Not-inserted layout
    private lateinit var step1Label: String
    private lateinit var step2Label: String
    private lateinit var step1Title: String
    private lateinit var step1Desc: String
    private lateinit var step2Title: String
    private lateinit var step2Desc: String
    private lateinit var warning: String
    private lateinit var checkButton: String
    private lateinit var retryButton: String

    // Inserted layout
    private lateinit var injectedTitle: String
    private lateinit var detachedButton: String

    // Discard dialog
    private lateinit var deactivateButton: String
    private lateinit var dialogTitle: String
    private lateinit var dialogDesc: String
    private lateinit var confirmButton: String
    private lateinit var cancelButton: String

    // Error banner messages
    private lateinit var bluetoothNotEnabledMsg: String
    private lateinit var notConnectedMsg: String
    private lateinit var profileNotSetMsg: String
    private lateinit var needleCheckFailedMsg: String
    private lateinit var discardFailedMsg: String
    private lateinit var setBasalFailedMsg: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()

        step1Label = context.getString(R.string.carelevo_patch_step_1)
        step2Label = context.getString(R.string.carelevo_patch_step_2)
        step1Title = context.getString(R.string.carelevo_patch_needle_insertion_step1_title)
        step1Desc = context.getString(R.string.carelevo_patch_needle_insertion_step1_desc)
        step2Title = context.getString(R.string.carelevo_patch_needle_insertion_step2_title)
        step2Desc = context.getString(R.string.carelevo_patch_needle_insertion_step2_desc)
        warning = context.getString(R.string.carelevo_patch_needle_insertion_desc_warning)
        checkButton = context.getString(R.string.carelevo_btn_needle_insert_check)
        retryButton = context.getString(R.string.carelevo_btn_retry)

        injectedTitle = context.getString(R.string.carelevo_dialog_patch_connect_needle_injected)
        detachedButton = context.getString(R.string.carelevo_dialog_connect_detached)

        deactivateButton = context.getString(R.string.carelevo_btn_patch_expiration)
        dialogTitle = context.getString(R.string.carelevo_dialog_patch_discard_message_title)
        dialogDesc = context.getString(R.string.carelevo_dialog_patch_discard_message_desc)
        confirmButton = context.getString(R.string.carelevo_btn_confirm)
        cancelButton = context.getString(R.string.carelevo_btn_cancel)

        bluetoothNotEnabledMsg = context.getString(R.string.carelevo_toast_msg_bluetooth_not_enabled)
        notConnectedMsg = context.getString(R.string.carelevo_toast_msg_not_connected_waiting_retry)
        profileNotSetMsg = context.getString(R.string.carelevo_toast_msg_profile_not_set)
        needleCheckFailedMsg = context.getString(R.string.carelevo_toast_msg_needle_check_failed)
        discardFailedMsg = context.getString(R.string.carelevo_toast_msg_discard_failed)
        setBasalFailedMsg = context.getString(R.string.carelevo_toast_msg_set_basal_failed)

        whenever(viewModel.isNeedleInsert).thenReturn(needleInsertedFlow)
        whenever(viewModel.event).thenReturn(eventFlow.asEventFlow())
        whenever(viewModel.isCreated).thenReturn(false)
    }

    /** Remaining-attempts label for a given failure count, mirroring the step's own clamping. */
    private fun retryCountText(failCount: Int): String =
        RuntimeEnvironment.getApplication().getString(
            R.string.carelevo_dialog_patch_needle_retry_count,
            (MAX_NEEDLE_CHECK_COUNT - failCount).coerceAtLeast(0)
        )

    /** Seeds `isNeedleInsert` and hosts the step, with `onExitFlow` wired to a counter. */
    private fun setScreen(needleInserted: Boolean = false) {
        needleInsertedFlow.value = needleInserted
        compose.setContent {
            MaterialTheme {
                CarelevoPatchFlowStep05NeedleInsertion(
                    viewModel = viewModel,
                    onExitFlow = { exitFlowCalls++ }
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Pushes an event through the mocked VM's flow and lets the step's collector + the resulting
     * recomposition settle. `emit` never suspends here (see [eventFlow]), so `runBlocking` cannot
     * deadlock the Robolectric main thread.
     */
    private fun emitEvent(event: Event) {
        runBlocking { eventFlow.emit(event) }
        compose.waitForIdle()
    }

    // ---------------------------------------------------------------------------------------------
    // First-composition side effects
    // ---------------------------------------------------------------------------------------------

    @Test
    fun firstComposition_whenNotCreated_observesPatchInfoAndMarksCreated() {
        whenever(viewModel.isCreated).thenReturn(false)

        setScreen()

        verify(viewModel).observePatchInfo()
        verify(viewModel).setIsCreated(true)
    }

    @Test
    fun firstComposition_whenAlreadyCreated_doesNotObservePatchInfoAgain() {
        whenever(viewModel.isCreated).thenReturn(true)

        setScreen()

        verify(viewModel, never()).observePatchInfo()
        verify(viewModel, never()).setIsCreated(true)
    }

    // ---------------------------------------------------------------------------------------------
    // Needle NOT inserted - insertion instructions
    // ---------------------------------------------------------------------------------------------

    @Test
    fun needleNotInserted_rendersBothStepSectionsWarningAndCheckButton() {
        setScreen(needleInserted = false)

        compose.onNodeWithText(step1Label).assertIsDisplayed()
        compose.onNodeWithText(step1Title).assertIsDisplayed()
        compose.onNodeWithText(step1Desc).assertIsDisplayed()
        compose.onNodeWithText(step2Label).assertIsDisplayed()
        compose.onNodeWithText(step2Title).assertIsDisplayed()
        compose.onNodeWithText(step2Desc).assertIsDisplayed()
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.onNodeWithText(checkButton).assertIsDisplayed()
        compose.onNodeWithText(deactivateButton).assertIsDisplayed()
    }

    @Test
    fun needleNotInserted_showsNeitherRetryStateNorErrorBannerNorDetachedLayout() {
        setScreen(needleInserted = false)

        // No failure yet: primary button is the first-attempt label, no remaining-attempts counter.
        compose.onNodeWithText(retryButton).assertDoesNotExist()
        compose.onNodeWithText(retryCountText(failCount = 1)).assertDoesNotExist()
        // No event yet: no banner.
        compose.onNodeWithText(needleCheckFailedMsg).assertDoesNotExist()
        // The detach-applicator layout is the other branch.
        compose.onNodeWithText(injectedTitle).assertDoesNotExist()
        compose.onNodeWithText(detachedButton).assertDoesNotExist()
        // The discard dialog is closed until the user asks for it.
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
    }

    @Test
    fun needleNotInserted_checkButtonIsEnabledAndStartsNeedleCheck() {
        setScreen(needleInserted = false)

        compose.onNodeWithText(checkButton).assertIsEnabled()
        compose.onNodeWithText(checkButton).performClick()

        verify(viewModel).startCheckNeedle()
    }

    @Test
    fun needleNotInserted_checkButtonClickClearsExistingErrorBanner() {
        setScreen(needleInserted = false)
        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleError)
        compose.onNodeWithText(needleCheckFailedMsg).assertIsDisplayed()

        compose.onNodeWithText(checkButton).performClick()

        compose.onNodeWithText(needleCheckFailedMsg).assertDoesNotExist()
        verify(viewModel).startCheckNeedle()
    }

    // ---------------------------------------------------------------------------------------------
    // Error banner - one test per event branch
    // ---------------------------------------------------------------------------------------------

    @Test
    fun bluetoothNotEnabledEvent_showsErrorBanner() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled)

        compose.onNodeWithText(bluetoothNotEnabledMsg).assertIsDisplayed()
    }

    @Test
    fun carelevoNotConnectedEvent_showsErrorBanner() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected)

        compose.onNodeWithText(notConnectedMsg).assertIsDisplayed()
    }

    @Test
    fun profileNotSetEvent_showsErrorBanner() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.ShowMessageProfileNotSet)

        compose.onNodeWithText(profileNotSetMsg).assertIsDisplayed()
    }

    @Test
    fun checkNeedleErrorEvent_showsErrorBanner() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleError)

        compose.onNodeWithText(needleCheckFailedMsg).assertIsDisplayed()
    }

    @Test
    fun setBasalFailedEvent_showsErrorBanner() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.SetBasalFailed)

        compose.onNodeWithText(setBasalFailedMsg).assertIsDisplayed()
    }

    @Test
    fun checkNeedleCompleteEvent_clearsErrorBanner() {
        setScreen()
        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleError)
        compose.onNodeWithText(needleCheckFailedMsg).assertIsDisplayed()

        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleComplete(result = true))

        compose.onNodeWithText(needleCheckFailedMsg).assertDoesNotExist()
    }

    @Test
    fun noActionEvent_leavesContentUnchangedAndDoesNotExitFlow() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.NoAction)

        compose.onNodeWithText(checkButton).assertIsDisplayed()
        compose.onNodeWithText(needleCheckFailedMsg).assertDoesNotExist()
        assertThat(exitFlowCalls).isEqualTo(0)
    }

    // ---------------------------------------------------------------------------------------------
    // Needle check failures - retry state and the exit-on-max threshold
    // ---------------------------------------------------------------------------------------------

    @Test
    fun checkNeedleFailedBelowMax_swapsToRetryButtonAndShowsRemainingAttempts() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount = 1))

        compose.onNodeWithText(retryButton).assertIsDisplayed()
        compose.onNodeWithText(checkButton).assertDoesNotExist()
        compose.onNodeWithText(retryCountText(failCount = 1)).assertIsDisplayed()
        assertThat(exitFlowCalls).isEqualTo(0)
    }

    @Test
    fun checkNeedleFailedBelowMax_retryButtonClearsBannerAndStartsNeedleCheck() {
        setScreen()
        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleError)
        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount = 2))
        compose.onNodeWithText(needleCheckFailedMsg).assertIsDisplayed()
        compose.onNodeWithText(retryCountText(failCount = 2)).assertIsDisplayed()

        compose.onNodeWithText(retryButton).performClick()

        compose.onNodeWithText(needleCheckFailedMsg).assertDoesNotExist()
        verify(viewModel).startCheckNeedle()
    }

    @Test
    fun checkNeedleFailedAtMax_exitsFlowAndShowsNoRemainingAttempts() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount = MAX_NEEDLE_CHECK_COUNT))

        assertThat(exitFlowCalls).isEqualTo(1)
        compose.onNodeWithText(retryCountText(failCount = MAX_NEEDLE_CHECK_COUNT)).assertIsDisplayed()
    }

    @Test
    fun checkNeedleFailedAboveMax_clampsRemainingAttemptsToZeroAndExitsFlow() {
        setScreen()

        emitEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount = MAX_NEEDLE_CHECK_COUNT + 2))

        assertThat(exitFlowCalls).isEqualTo(1)
        // (3 - 5) would be negative; the step coerces it to 0 rather than showing "-2 attempts left".
        compose.onNodeWithText(retryCountText(failCount = MAX_NEEDLE_CHECK_COUNT + 2)).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // Discard dialog
    // ---------------------------------------------------------------------------------------------

    @Test
    fun deactivateButton_isEnabledAndOpensDiscardDialog() {
        setScreen()

        compose.onNodeWithText(deactivateButton).assertIsEnabled()
        compose.onNodeWithText(deactivateButton).performClick()

        compose.onNodeWithText(dialogTitle).assertIsDisplayed()
        compose.onNodeWithText(dialogDesc).assertIsDisplayed()
        compose.onNodeWithText(confirmButton).assertIsDisplayed()
        compose.onNodeWithText(cancelButton).assertIsDisplayed()
        verify(viewModel, never()).startDiscardProcess()
    }

    @Test
    fun discardDialog_confirmStartsDiscardAndClosesDialog() {
        setScreen()
        compose.onNodeWithText(deactivateButton).performClick()

        compose.onNodeWithText(confirmButton).performClick()

        verify(viewModel).startDiscardProcess()
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
    }

    @Test
    fun discardDialog_cancelClosesDialogWithoutStartingDiscard() {
        setScreen()
        compose.onNodeWithText(deactivateButton).performClick()

        compose.onNodeWithText(cancelButton).performClick()

        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        verify(viewModel, never()).startDiscardProcess()
    }

    @Test
    fun discardCompleteEvent_closesDialogAndExitsFlow() {
        setScreen()
        compose.onNodeWithText(deactivateButton).performClick()
        compose.onNodeWithText(dialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectNeedleEvent.DiscardComplete)

        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        assertThat(exitFlowCalls).isEqualTo(1)
    }

    @Test
    fun discardFailedEvent_closesDialogShowsErrorBannerAndStaysInFlow() {
        setScreen()
        compose.onNodeWithText(deactivateButton).performClick()
        compose.onNodeWithText(dialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectNeedleEvent.DiscardFailed)

        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        compose.onNodeWithText(discardFailedMsg).assertIsDisplayed()
        assertThat(exitFlowCalls).isEqualTo(0)
    }

    // ---------------------------------------------------------------------------------------------
    // Needle inserted - detach applicator / start delivery
    // ---------------------------------------------------------------------------------------------

    @Test
    fun needleInserted_rendersInjectedMessageGuideAndDetachedButton() {
        setScreen(needleInserted = true)

        compose.onNodeWithText(injectedTitle).assertIsDisplayed()
        // The guide is HTML-rendered from a multi-line resource, so match a stable fragment only.
        compose.onNodeWithText(GUIDE_FRAGMENT, substring = true).assertIsDisplayed()
        compose.onNodeWithText(detachedButton).assertIsDisplayed()
        compose.onNodeWithText(deactivateButton).assertIsDisplayed()
    }

    @Test
    fun needleInserted_doesNotRenderInsertionInstructions() {
        setScreen(needleInserted = true)

        compose.onNodeWithText(step1Label).assertDoesNotExist()
        compose.onNodeWithText(step2Label).assertDoesNotExist()
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithText(checkButton).assertDoesNotExist()
        compose.onNodeWithText(retryButton).assertDoesNotExist()
    }

    @Test
    fun needleInserted_detachedButtonIsEnabledAndStartsBasal() {
        setScreen(needleInserted = true)

        compose.onNodeWithText(detachedButton).assertIsEnabled()
        compose.onNodeWithText(detachedButton).performClick()

        verify(viewModel).startSetBasal()
    }

    @Test
    fun needleInserted_setBasalFailedEvent_showsErrorBannerInDetachLayout() {
        setScreen(needleInserted = true)

        emitEvent(CarelevoConnectNeedleEvent.SetBasalFailed)

        compose.onNodeWithText(setBasalFailedMsg).assertIsDisplayed()
        compose.onNodeWithText(injectedTitle).assertIsDisplayed()
    }

    @Test
    fun needleInserted_detachedButtonClickClearsExistingErrorBanner() {
        setScreen(needleInserted = true)
        emitEvent(CarelevoConnectNeedleEvent.SetBasalFailed)
        compose.onNodeWithText(setBasalFailedMsg).assertIsDisplayed()

        compose.onNodeWithText(detachedButton).performClick()

        compose.onNodeWithText(setBasalFailedMsg).assertDoesNotExist()
        verify(viewModel).startSetBasal()
    }

    @Test
    fun needleInserted_setBasalCompleteEvent_exitsFlow() {
        setScreen(needleInserted = true)

        emitEvent(CarelevoConnectNeedleEvent.SetBasalComplete)

        assertThat(exitFlowCalls).isEqualTo(1)
    }

    @Test
    fun needleInserted_deactivateButtonOpensDiscardDialog() {
        setScreen(needleInserted = true)

        compose.onNodeWithText(deactivateButton).performClick()

        compose.onNodeWithText(dialogTitle).assertIsDisplayed()
        compose.onNodeWithText(confirmButton).assertIsDisplayed()
    }

    companion object {

        /** Mirrors the step's own private `MAX_NEEDLE_CHECK_COUNT`. */
        private const val MAX_NEEDLE_CHECK_COUNT = 3

        /** A single-line, whitespace-stable fragment of the detach-applicator guide resource. */
        private const val GUIDE_FRAGMENT = "Insulin delivery will begin."
    }
}
