package app.aaps.pump.carelevo.compose.patchflow

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectSafetyCheckEvent
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchSafetyCheckViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI test for [CarelevoPatchFlowStep03SafetyCheck] - step 3 of the patch activation wizard, where the
 * ~100-210 s safety check streams its countdown and the discard escape hatch is gated.
 *
 * ## How the ViewModels are injected
 * The composable takes both VMs as plain parameters (the `hiltViewModel()` defaults live in
 * `CarelevoPatchFlowScreen`, not here), so both are Mockito doubles - the module already mocks final
 * Kotlin classes (see `CarelevoPatchSafetyCheckViewModelTest`, which mocks `CarelevoActivationExecutor`),
 * so the inline mock maker covers these final `@HiltViewModel` classes too. The two StateFlows the
 * screen collects are stubbed with real [MutableStateFlow]s and the one-shot event stream with a real
 * production [MutableEventFlow] - so the collect/consume semantics under test are the real ones, and
 * the test drives every UI state by emitting the same events the VM would.
 *
 * The VM's own logic (progress arithmetic, queue routing, ticker) is covered by
 * `CarelevoPatchSafetyCheckViewModelTest`; this test covers only what the composable decides: the
 * Ready/Progress/Success state machine, the error banner, the discard gate and the callbacks.
 *
 * ## Two conventions that matter
 * - `progress`/`remainSec` are **seeded before `setContent`**: they are read through
 *   `collectAsStateWithLifecycle`, whose initial value is the StateFlow's current value, so seeding
 *   up-front exercises every rendering branch without depending on the test host's lifecycle state.
 * - [emitEvent] brackets each emit with `waitForIdle()`. The leading one guarantees the composable's
 *   collector has consumed the previous slot, so the replay-1 `MutableEventFlow` always has a free
 *   buffer slot and `emit` cannot suspend inside `runBlocking` on the test (= main) thread; the
 *   trailing one lets the collector apply the event and recompose before the assertions run.
 *
 * `MaterialTheme` alone is enough (mirrors the file's own `@Preview`s): the screen and its shared
 * `WizardStepLayout`/`ErrorBanner` read only `MaterialTheme` values plus the plain `AapsSpacing`
 * object - no `AapsTheme`, hence no `LocalPreferences`.
 *
 * A phone-sized qualifier is forced so the tallest state (Success: title + desc + bar + countdown row
 * + warning + retry desc + retry button, over the pinned button row) fits and `assertIsDisplayed`
 * stays meaningful.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class CarelevoPatchFlowStep03SafetyCheckTest {

    @get:Rule
    val compose = createComposeRule()

    /** The real production event flow - the screen's `LaunchedEffect` collects it exactly as it would the VM's. */
    private val eventSource = MutableEventFlow<Event>()
    private val progressFlow = MutableStateFlow<Int?>(null)
    private val remainSecFlow = MutableStateFlow<Long?>(null)

    private var exitFlowCount = 0

    private lateinit var viewModel: CarelevoPatchSafetyCheckViewModel
    private lateinit var sharedViewModel: CarelevoPatchConnectionFlowViewModel

    // Labels resolved from resources rather than hardcoded, so the test survives copy/locale changes.
    private lateinit var startTitle: String
    private lateinit var endTitle: String
    private lateinit var startDesc: String
    private lateinit var progressDesc: String
    private lateinit var endDesc: String
    private lateinit var warningText: String
    private lateinit var retryDesc: String
    private lateinit var btnSafetyCheck: String
    private lateinit var btnNext: String
    private lateinit var btnDiscard: String
    private lateinit var btnRetry: String
    private lateinit var btnConfirm: String
    private lateinit var btnCancel: String
    private lateinit var dialogTitle: String
    private lateinit var dialogDesc: String
    private lateinit var errBluetoothOff: String
    private lateinit var errNotConnected: String
    private lateinit var errSafetyCheckFailed: String
    private lateinit var errDiscardFailed: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        startTitle = context.getString(R.string.carelevo_patch_safety_check_start_title)
        endTitle = context.getString(R.string.carelevo_patch_safety_check_end_title)
        startDesc = context.getString(R.string.carelevo_patch_safety_check_start_desc)
        progressDesc = context.getString(R.string.carelevo_patch_safety_check_progress_desc)
        endDesc = context.getString(R.string.carelevo_patch_safety_check_end_desc)
        warningText = context.getString(R.string.carelevo_patch_safety_check_desc_warning)
        retryDesc = context.getString(R.string.carelevo_patch_safety_check_retry_desc)
        btnSafetyCheck = context.getString(R.string.carelevo_btn_safety_check)
        btnNext = context.getString(R.string.carelevo_btn_next)
        btnDiscard = context.getString(R.string.carelevo_btn_patch_expiration)
        btnRetry = context.getString(R.string.carelevo_btn_retry)
        btnConfirm = context.getString(R.string.carelevo_btn_confirm)
        btnCancel = context.getString(R.string.carelevo_btn_cancel)
        dialogTitle = context.getString(R.string.carelevo_dialog_patch_discard_message_title)
        dialogDesc = context.getString(R.string.carelevo_dialog_patch_discard_message_desc)
        errBluetoothOff = context.getString(R.string.carelevo_toast_msg_bluetooth_not_enabled)
        errNotConnected = context.getString(R.string.carelevo_toast_msg_not_connected_waiting_retry)
        errSafetyCheckFailed = context.getString(R.string.carelevo_toast_msg_safety_check_failed)
        errDiscardFailed = context.getString(R.string.carelevo_toast_msg_discard_failed)

        viewModel = mock {
            on { progress } doReturn progressFlow
            on { remainSec } doReturn remainSecFlow
            on { event } doReturn eventSource.asEventFlow()
            on { isSafetyCheckPassed() } doReturn false
            on { isCreated } doReturn false
        }
        sharedViewModel = mock()
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private fun setScreen() {
        compose.setContent {
            MaterialTheme {
                CarelevoPatchFlowStep03SafetyCheck(
                    viewModel = viewModel,
                    sharedViewModel = sharedViewModel,
                    onExitFlow = { exitFlowCount++ }
                )
            }
        }
        // Let the two LaunchedEffects run, so the event collector is subscribed before any emit.
        compose.waitForIdle()
    }

    /** Push a one-shot event through the real EventFlow, exactly as the VM's `triggerEvent` would. */
    private fun emitEvent(event: CarelevoConnectSafetyCheckEvent) {
        compose.waitForIdle()
        runBlocking { eventSource.emit(event) }
        compose.waitForIdle()
    }

    /** Seed the countdown StateFlows. Must run before [setScreen] - see the class KDoc. */
    private fun seedCountdown(progress: Int?, remainSec: Long?) {
        progressFlow.value = progress
        remainSecFlow.value = remainSec
    }

    private fun remainText(minutes: Long, seconds: Long): String =
        RuntimeEnvironment.getApplication().getString(R.string.common_unit_remain_min_sec, minutes, seconds)

    private fun progressText(percent: Int): String =
        RuntimeEnvironment.getApplication().getString(R.string.carelevo_progress_of_100, percent)

    /**
     * The Ready title and the primary button share the literal "Safety Check", so the button must be
     * addressed by its click action to stay unambiguous in the merged tree.
     */
    private fun safetyCheckButton() = compose.onNode(hasText(btnSafetyCheck) and hasClickAction())

    private fun progressBar(fraction: Float) =
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(fraction, 0f..1f)))

    // ---- Ready (initial, safety check not yet passed) ----------------------------------------------

    @Test
    fun ready_showsStartTitleDescAndSafetyCheckButton() {
        setScreen()

        compose.onNodeWithText(startDesc).assertIsDisplayed()
        safetyCheckButton().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(endTitle).assertDoesNotExist()
        compose.onNodeWithText(btnNext).assertDoesNotExist()
    }

    @Test
    fun ready_hidesRetrySection() {
        setScreen()

        compose.onNodeWithText(warningText).assertDoesNotExist()
        compose.onNodeWithText(retryDesc).assertDoesNotExist()
        compose.onNodeWithText(btnRetry).assertDoesNotExist()
    }

    @Test
    fun ready_showsNoErrorBanner() {
        setScreen()

        compose.onNodeWithText(errBluetoothOff).assertDoesNotExist()
        compose.onNodeWithText(errNotConnected).assertDoesNotExist()
        compose.onNodeWithText(errSafetyCheckFailed).assertDoesNotExist()
        compose.onNodeWithText(errDiscardFailed).assertDoesNotExist()
    }

    @Test
    fun ready_discardButtonIsEnabled() {
        setScreen()

        compose.onNodeWithText(btnDiscard).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun ready_suppressesCountdownRow_evenWhenTheFlowsAlreadyCarryValues() {
        // showProgressDetails is false before the check starts: a stale countdown left over from the
        // VM must not leak onto the start screen.
        seedCountdown(progress = 50, remainSec = 90L)
        setScreen()

        compose.onNodeWithText(progressText(50)).assertDoesNotExist()
        compose.onNodeWithText(remainText(1, 30)).assertDoesNotExist()
    }

    @Test
    fun ready_progressBarStaysEmpty_evenWhenProgressIsSeeded() {
        seedCountdown(progress = 50, remainSec = 90L)
        setScreen()

        progressBar(0f).assertExists()
    }

    // ---- LaunchedEffect: created latch + already-passed short circuit ------------------------------

    @Test
    fun onFirstComposition_marksTheViewModelCreated() {
        setScreen()

        verify(viewModel).setIsCreated(true)
    }

    @Test
    fun onRecreation_doesNotReMarkAnAlreadyCreatedViewModel() {
        whenever(viewModel.isCreated).thenReturn(true)

        setScreen()

        verify(viewModel, never()).setIsCreated(true)
    }

    @Test
    fun whenSafetyCheckAlreadyPassed_replaysCompletionIntoTheViewModel() {
        whenever(viewModel.isSafetyCheckPassed()).thenReturn(true)

        setScreen()

        verify(viewModel).onSafetyCheckComplete()
    }

    @Test
    fun whenSafetyCheckNotPassed_doesNotReplayCompletion() {
        setScreen()

        verify(viewModel, never()).onSafetyCheckComplete()
    }

    @Test
    fun whenSafetyCheckAlreadyPassed_opensDirectlyInTheSuccessState() {
        // Cold-load into the wizard after a passed check: the step must not ask for the check again.
        whenever(viewModel.isSafetyCheckPassed()).thenReturn(true)
        seedCountdown(progress = 100, remainSec = 0L)
        setScreen()

        compose.onNodeWithText(endTitle).assertIsDisplayed()
        compose.onNodeWithText(endDesc).assertIsDisplayed()
        compose.onNodeWithText(btnNext).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(btnRetry).assertIsDisplayed()
        safetyCheckButton().assertDoesNotExist()
    }

    // ---- Ready interactions -----------------------------------------------------------------------

    @Test
    fun safetyCheckButtonClick_startsTheCheck() {
        setScreen()

        safetyCheckButton().performClick()
        compose.waitForIdle()

        verify(viewModel).startSafetyCheck()
    }

    @Test
    fun safetyCheckButtonClick_clearsAPreviousErrorBanner() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
        compose.onNodeWithText(errSafetyCheckFailed).assertIsDisplayed()

        safetyCheckButton().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(errSafetyCheckFailed).assertDoesNotExist()
        verify(viewModel).startSafetyCheck()
    }

    // ---- Progress ---------------------------------------------------------------------------------

    @Test
    fun progressEvent_keepsStartTitleAndSwapsInTheProgressDesc() {
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(startTitle).assertIsDisplayed()
        compose.onNodeWithText(progressDesc).assertIsDisplayed()
        compose.onNodeWithText(startDesc).assertDoesNotExist()
        compose.onNodeWithText(endDesc).assertDoesNotExist()
    }

    @Test
    fun progressEvent_replacesTheSafetyCheckButtonWithADisabledNext() {
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        safetyCheckButton().assertDoesNotExist()
        compose.onNodeWithText(btnNext).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun progressEvent_disablesTheDiscardEscapeHatch() {
        // The gate: a CmdDiscard enqueued behind the still-running CmdSafetyCheck would interleave
        // confusingly, so discard must be unreachable while the check streams.
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(btnDiscard).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun progressEvent_disabledDiscardTap_cannotOpenTheDialog() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(btnDiscard).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        verify(viewModel, never()).startDiscardProcess()
    }

    @Test
    fun progressEvent_hidesTheRetrySection() {
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(warningText).assertDoesNotExist()
        compose.onNodeWithText(retryDesc).assertDoesNotExist()
        compose.onNodeWithText(btnRetry).assertDoesNotExist()
    }

    @Test
    fun progressEvent_showsCountdownAndPercent() {
        seedCountdown(progress = 50, remainSec = 90L)
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(remainText(1, 30)).assertIsDisplayed()
        compose.onNodeWithText(progressText(50)).assertIsDisplayed()
    }

    @Test
    fun progressEvent_fillsTheBarToTheReportedPercent() {
        seedCountdown(progress = 50, remainSec = 90L)
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        progressBar(0.5f).assertExists()
    }

    @Test
    fun progressEvent_withoutAnyCountdownValues_hidesTheDetailRowAndKeepsTheBarEmpty() {
        seedCountdown(progress = null, remainSec = null)
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        // Nothing to show yet (no Progress frame from the executor) - no "x/100", no countdown.
        compose.onNodeWithText("/100", substring = true).assertDoesNotExist()
        compose.onNodeWithText(remainText(0, 0)).assertDoesNotExist()
        // ...and `progress ?: 0` keeps the bar at zero rather than crashing on the null.
        progressBar(0f).assertExists()
        compose.onNodeWithText(progressDesc).assertIsDisplayed()
    }

    @Test
    fun progressEvent_withOnlyRemainSec_showsTheCountdownAndNoPercent() {
        seedCountdown(progress = null, remainSec = 45L)
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(remainText(0, 45)).assertIsDisplayed()
        compose.onNodeWithText("/100", substring = true).assertDoesNotExist()
    }

    @Test
    fun progressEvent_withOnlyPercent_showsThePercentAndNoCountdown() {
        seedCountdown(progress = 25, remainSec = null)
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(progressText(25)).assertIsDisplayed()
        progressBar(0.25f).assertExists()
        compose.onNodeWithText(remainText(0, 0)).assertDoesNotExist()
    }

    @Test
    fun progressEvent_clearsAPreviousErrorBanner() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
        compose.onNodeWithText(errBluetoothOff).assertIsDisplayed()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)

        compose.onNodeWithText(errBluetoothOff).assertDoesNotExist()
        compose.onNodeWithText(progressDesc).assertIsDisplayed()
    }

    // ---- Success ----------------------------------------------------------------------------------

    @Test
    fun completeEvent_showsEndTitleDescAndRetrySection() {
        seedCountdown(progress = 100, remainSec = 0L)
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)

        compose.onNodeWithText(endTitle).assertIsDisplayed()
        compose.onNodeWithText(endDesc).assertIsDisplayed()
        compose.onNodeWithText(warningText).assertIsDisplayed()
        compose.onNodeWithText(retryDesc).assertIsDisplayed()
        compose.onNodeWithText(btnRetry).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(startTitle).assertDoesNotExist()
    }

    @Test
    fun completeEvent_enablesNextAndRestoresTheDiscardEscapeHatch() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)
        compose.onNodeWithText(btnDiscard).assertIsNotEnabled()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)

        compose.onNodeWithText(btnNext).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(btnDiscard).assertIsEnabled()
        safetyCheckButton().assertDoesNotExist()
    }

    @Test
    fun completeEvent_showsAFullBarAndFinishedCountdown() {
        seedCountdown(progress = 100, remainSec = 0L)
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)

        progressBar(1f).assertExists()
        compose.onNodeWithText(progressText(100)).assertIsDisplayed()
        compose.onNodeWithText(remainText(0, 0)).assertIsDisplayed()
    }

    @Test
    fun completeEvent_clearsAPreviousErrorBanner() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
        compose.onNodeWithText(errSafetyCheckFailed).assertIsDisplayed()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)

        compose.onNodeWithText(errSafetyCheckFailed).assertDoesNotExist()
        compose.onNodeWithText(endTitle).assertIsDisplayed()
    }

    @Test
    fun nextButtonClick_advancesTheSharedFlow() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)

        compose.onNodeWithText(btnNext).performClick()
        compose.waitForIdle()

        verify(sharedViewModel).advanceFromSafetyCheck()
    }

    @Test
    fun retryButtonClick_retriesAdditionalPriming() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)

        compose.onNodeWithText(btnRetry).performClick()
        compose.waitForIdle()

        verify(viewModel).retryAdditionalPriming()
        verify(sharedViewModel, never()).advanceFromSafetyCheck()
    }

    @Test
    fun retryButtonClick_clearsAPreviousErrorBanner() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
        emitEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
        compose.onNodeWithText(errDiscardFailed).assertIsDisplayed()

        compose.onNodeWithText(btnRetry).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(errDiscardFailed).assertDoesNotExist()
        verify(viewModel).retryAdditionalPriming()
    }

    // ---- Failure event ----------------------------------------------------------------------------

    @Test
    fun failedEvent_bannersTheFailureAndFallsBackToReady() {
        whenever(viewModel.isSafetyCheckPassed()).thenReturn(true)
        setScreen()
        compose.onNodeWithText(endTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)

        compose.onNodeWithText(errSafetyCheckFailed).assertIsDisplayed()
        // Back to Ready: the check is offered again and the success-only affordances are gone.
        compose.onNodeWithText(startDesc).assertIsDisplayed()
        safetyCheckButton().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(endTitle).assertDoesNotExist()
        compose.onNodeWithText(btnRetry).assertDoesNotExist()
        compose.onNodeWithText(btnNext).assertDoesNotExist()
    }

    @Test
    fun failedEvent_resetsTheVisibleCountdown_ratherThanStrandingItHalfRun() {
        seedCountdown(progress = 50, remainSec = 30L)
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)
        compose.onNodeWithText(progressText(50)).assertIsDisplayed()

        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)

        // The VM deliberately freezes its own progress/remainSec on failure, so the abandoned 50 %
        // is still in the flows. Falling back to Ready must drop showProgressDetails and blank the
        // bar, otherwise a stale half-run countdown sits above an untouched [Safety Check] button.
        progressBar(0f).assertExists()
        compose.onNodeWithText(progressText(50)).assertDoesNotExist()
        compose.onNodeWithText(remainText(0, 30)).assertDoesNotExist()
    }

    // ---- Message-only events ----------------------------------------------------------------------

    @Test
    fun bluetoothNotEnabledEvent_bannersWithoutLeavingReady() {
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)

        compose.onNodeWithText(errBluetoothOff).assertIsDisplayed()
        compose.onNodeWithText(startDesc).assertIsDisplayed()
        safetyCheckButton().assertIsEnabled()
    }

    @Test
    fun notConnectedEvent_bannersWithoutLeavingReady() {
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)

        compose.onNodeWithText(errNotConnected).assertIsDisplayed()
        compose.onNodeWithText(startDesc).assertIsDisplayed()
        safetyCheckButton().assertIsEnabled()
    }

    @Test
    fun noActionEvent_changesNothing() {
        setScreen()

        emitEvent(CarelevoConnectSafetyCheckEvent.NoAction)

        compose.onNodeWithText(startDesc).assertIsDisplayed()
        safetyCheckButton().assertIsEnabled()
        compose.onNodeWithText(btnDiscard).assertIsEnabled()
        compose.onNodeWithText(errBluetoothOff).assertDoesNotExist()
        compose.onNodeWithText(errSafetyCheckFailed).assertDoesNotExist()
        assertThat(exitFlowCount).isEqualTo(0)
    }

    @Test
    fun aSecondBannerEvent_replacesTheFirstMessage() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)

        emitEvent(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)

        compose.onNodeWithText(errNotConnected).assertIsDisplayed()
        compose.onNodeWithText(errBluetoothOff).assertDoesNotExist()
    }

    // ---- Discard dialog ---------------------------------------------------------------------------

    @Test
    fun discardButtonClick_opensTheConfirmationDialog() {
        setScreen()

        compose.onNodeWithText(btnDiscard).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(dialogTitle).assertIsDisplayed()
        compose.onNodeWithText(dialogDesc).assertIsDisplayed()
        compose.onNodeWithText(btnConfirm).assertIsDisplayed()
        compose.onNodeWithText(btnCancel).assertIsDisplayed()
        // Opening the dialog must not itself start anything.
        verify(viewModel, never()).startDiscardProcess()
    }

    @Test
    fun discardDialogConfirm_startsDiscardAndClosesTheDialog() {
        setScreen()
        compose.onNodeWithText(btnDiscard).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(btnConfirm).performClick()
        compose.waitForIdle()

        verify(viewModel).startDiscardProcess()
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        // The flow is only exited once the VM reports DiscardComplete, not on the tap.
        assertThat(exitFlowCount).isEqualTo(0)
    }

    @Test
    fun discardDialogCancel_closesTheDialogWithoutDiscarding() {
        setScreen()
        compose.onNodeWithText(btnDiscard).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(btnCancel).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        verify(viewModel, never()).startDiscardProcess()
        assertThat(exitFlowCount).isEqualTo(0)
    }

    @Test
    fun discardCompleteEvent_closesTheDialogAndExitsTheFlow() {
        setScreen()
        compose.onNodeWithText(btnDiscard).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(dialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)

        assertThat(exitFlowCount).isEqualTo(1)
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
    }

    @Test
    fun discardFailedEvent_closesTheDialogAndBannersTheFailure() {
        setScreen()
        compose.onNodeWithText(btnDiscard).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(dialogTitle).assertIsDisplayed()

        emitEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)

        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        compose.onNodeWithText(errDiscardFailed).assertIsDisplayed()
        // A failed discard must not silently drop the user out of the wizard.
        assertThat(exitFlowCount).isEqualTo(0)
        // ...and the step stays usable.
        compose.onNodeWithText(startDesc).assertIsDisplayed()
        safetyCheckButton().assertIsEnabled()
    }

    @Test
    fun discardDialog_isReachableFromTheSuccessStateToo() {
        setScreen()
        emitEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)

        compose.onNodeWithText(btnDiscard).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(btnConfirm).performClick()
        compose.waitForIdle()

        verify(viewModel).startDiscardProcess()
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
    }
}
