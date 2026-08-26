package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.config.FillConfig
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI test for [CarelevoPatchFlowStep01Start] — the first step of the patch activation wizard — and for
 * [patchStepTitle], the shared title mapper every step of that wizard is labelled with.
 *
 * **ViewModel:** the step takes a real [CarelevoPatchConnectionFlowViewModel] built from all-mocked
 * collaborators rather than a mock of the ViewModel itself. Construction touches none of the ten
 * dependencies (every field is a plain initialiser) and the only member the step calls —
 * `setPage` — is pure state (`tryEmit` + a `workflowSteps.indexOf`), so the real object works and lets
 * the Next click be asserted against observable state (`page.value`) instead of a mock interaction.
 * No `Dispatchers.setMain` is needed: nothing here reaches `viewModelScope`.
 *
 * **Theme:** [MaterialTheme] only — the step and `WizardStepLayout` read `MaterialTheme.typography` /
 * `colorScheme` but no `AapsTheme` values, so no `LocalPreferences` is required (this mirrors the
 * step's own `@Preview`).
 *
 * Labels are read back through `getString` so the assertions track the resources, not hardcoded
 * English. A phone-sized qualifier is forced so the pinned action row and the collapsed content fit on
 * screen; the twelve expanded refill steps are far taller than any display, so those are asserted with
 * `assertExists` (the guide is a plain scrolling `Column`, so every entry is composed) rather than
 * `assertIsDisplayed`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class CarelevoPatchFlowStep01StartTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var viewModel: CarelevoPatchConnectionFlowViewModel
    private var exitFlowCalls = 0

    private lateinit var title: String
    private lateinit var notice: String
    private lateinit var guideLabel: String
    private lateinit var nextLabel: String
    private lateinit var cancelLabel: String
    private lateinit var refillSteps: List<String>

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        title = app.getString(R.string.carelevo_title_fill_insulin)
        notice = app.getString(R.string.carelevo_notice_fill_insulin_amount, FillConfig.FILL_MIN_UNITS, FillConfig.FILL_MAX_UNITS)
        guideLabel = app.getString(R.string.carelevo_btn_insulin_guide)
        nextLabel = app.getString(CoreUiR.string.next)
        cancelLabel = app.getString(CoreUiR.string.cancel)
        refillSteps = listOf(
            app.getString(R.string.carelevo_insulin_refill_step1),
            app.getString(R.string.carelevo_insulin_refill_step2),
            app.getString(R.string.carelevo_insulin_refill_step3),
            app.getString(R.string.carelevo_insulin_refill_step4),
            app.getString(R.string.carelevo_insulin_refill_step5),
            app.getString(R.string.carelevo_insulin_refill_step6),
            app.getString(R.string.carelevo_insulin_refill_step7),
            app.getString(R.string.carelevo_insulin_refill_step8),
            app.getString(R.string.carelevo_insulin_refill_step9),
            app.getString(R.string.carelevo_insulin_refill_step10),
            app.getString(R.string.carelevo_insulin_refill_step11),
            app.getString(R.string.carelevo_insulin_refill_step12)
        )

        exitFlowCalls = 0
        viewModel = CarelevoPatchConnectionFlowViewModel(
            aapsLogger = mock(),
            aapsSchedulers = mock(),
            carelevoPatch = mock(),
            commandQueue = mock(),
            patchForceDiscardUseCase = mock(),
            preferences = mock(),
            profileFunction = mock(),
            profileRepository = mock(),
            insulinManager = mock(),
            persistenceLayer = mock()
        )
    }

    /** Hosts the step under test with the exit callback wired to a counter. */
    private fun setStep() {
        compose.setContent {
            MaterialTheme {
                CarelevoPatchFlowStep01Start(
                    viewModel = viewModel,
                    onExitFlow = { exitFlowCalls++ }
                )
            }
        }
    }

    /** The collapsed guide's trigger: the only node carrying the guide label *and* a click action. */
    private fun guideButton() = compose.onNode(hasText(guideLabel) and hasClickAction())

    @Test
    fun initialState_showsTitleNoticeAndBothActions() {
        setStep()

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(notice).assertIsDisplayed()
        compose.onNodeWithText(nextLabel).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun initialState_guideIsCollapsed_showingOnlyItsButton() {
        setStep()

        guideButton().assertIsDisplayed().assertIsEnabled()
        // Collapsed: the label exists exactly once (the button), and no refill step is composed.
        compose.onAllNodesWithText(guideLabel).assertCountEquals(1)
        refillSteps.forEach { step ->
            compose.onNodeWithText(step).assertDoesNotExist()
        }
    }

    @Test
    fun initialState_viewModelStaysOnPatchStart() {
        setStep()

        assertThat(viewModel.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
        assertThat(exitFlowCalls).isEqualTo(0)
    }

    @Test
    fun nextButton_advancesViewModelToSetAmount_withoutExitingFlow() {
        setStep()

        compose.onNodeWithText(nextLabel).performClick()

        assertThat(viewModel.page.value).isEqualTo(CarelevoPatchStep.SET_AMOUNT)
        assertThat(exitFlowCalls).isEqualTo(0)
    }

    @Test
    fun cancelButton_invokesOnExitFlow_andLeavesThePageUnchanged() {
        setStep()

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(exitFlowCalls).isEqualTo(1)
        assertThat(viewModel.page.value).isEqualTo(CarelevoPatchStep.PATCH_START)
    }

    @Test
    fun guideButton_whenClicked_expandsAndRendersAllTwelveRefillSteps() {
        setStep()

        guideButton().performClick()

        refillSteps.forEach { step ->
            compose.onNodeWithText(step).assertExists()
        }
    }

    @Test
    fun guideButton_whenExpanded_isReplacedByAPlainHeading() {
        setStep()

        guideButton().performClick()

        // The label survives as the section heading, but the clickable trigger is gone.
        guideButton().assertDoesNotExist()
        compose.onAllNodesWithText(guideLabel).assertCountEquals(1)
        compose.onNodeWithText(guideLabel).assertExists()
    }

    @Test
    fun guideExpansion_doesNotDisturbTheStepContentOrActions() {
        setStep()

        guideButton().performClick()

        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithText(notice).assertExists()
        compose.onNodeWithText(nextLabel).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun expandedGuide_survivesRecomposition_andNextStillAdvances() {
        setStep()

        guideButton().performClick()
        compose.onNodeWithText(nextLabel).performClick()

        assertThat(viewModel.page.value).isEqualTo(CarelevoPatchStep.SET_AMOUNT)
        // The remembered expansion is unaffected by the click that recomposed the step.
        compose.onNodeWithText(refillSteps.first()).assertExists()
        guideButton().assertDoesNotExist()
    }

    @Test
    fun cancelButton_whenClickedTwice_reportsEveryExitRequest() {
        setStep()

        compose.onNodeWithText(cancelLabel).performClick()
        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(exitFlowCalls).isEqualTo(2)
    }

    @Test
    fun patchStepTitle_labelsEveryWizardStep() {
        val app = RuntimeEnvironment.getApplication()
        val expected = mapOf(
            CarelevoPatchStep.PROFILE_GATE to app.getString(CoreUiR.string.pump_wizard_profile_gate_title),
            CarelevoPatchStep.SELECT_INSULIN to app.getString(CoreUiR.string.select_insulin),
            CarelevoPatchStep.PATCH_START to app.getString(R.string.carelevo_connect_prepare_title),
            CarelevoPatchStep.SET_AMOUNT to app.getString(R.string.patch_prepare_dialog_title_insulin_amount),
            CarelevoPatchStep.PATCH_CONNECT to app.getString(R.string.carelevo_connect_patch_title),
            CarelevoPatchStep.SAFETY_CHECK to app.getString(R.string.carelevo_connect_safety_check_title),
            CarelevoPatchStep.SITE_LOCATION to app.getString(CoreUiR.string.site_rotation),
            CarelevoPatchStep.PATCH_ATTACH to app.getString(R.string.carelevo_connect_patch_attach_title),
            CarelevoPatchStep.NEEDLE_INSERTION to app.getString(R.string.carelevo_connect_needle_check_title)
        )

        compose.setContent {
            MaterialTheme {
                Column {
                    CarelevoPatchStep.entries.forEach { step ->
                        Text(
                            text = patchStepTitle(step),
                            modifier = Modifier.testTag(step.name)
                        )
                    }
                }
            }
        }

        // Guards the `when` against a new enum constant being added without a title.
        assertThat(expected.keys).containsExactlyElementsIn(CarelevoPatchStep.entries)
        expected.forEach { (step, label) ->
            compose.onNodeWithTag(step.name).assertTextEquals(label)
        }
    }
}
