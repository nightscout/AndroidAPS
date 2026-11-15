package app.aaps.pump.eopatch.compose.steps

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel
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
 * Covers the buttons on the patch activation wizard steps.
 *
 * Each step is a small screen with a Next and usually a Discard button, and the only thing it does
 * is tell the view model where to go. That makes a wrong target easy to write and impossible to see
 * in review: the wizard would simply skip a step. Skipping matters here - these steps walk the user
 * through attaching a patch to their body, and the order is the order of the physical actions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardStepNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val viewModel: EopatchPatchViewModel = mock()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val next: String get() = context.getString(CoreUiR.string.next)
    private val discard: String get() = context.getString(CoreUiR.string.discard)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { MaterialTheme { content() } }
    }

    // ---- RemoveNeedleCapStep: the one step that chooses between two targets ----

    /** With site selection switched on, the next step is where the user picks the site. */
    @Test
    fun removeNeedleCap_goesToSiteLocationWhenThatStepIsShown() {
        whenever(viewModel.showSiteLocationStep).thenReturn(true)
        show { RemoveNeedleCapStep(viewModel) }

        compose.onNodeWithText(next).performClick()

        verify(viewModel).moveStep(PatchStep.SITE_LOCATION)
        verify(viewModel, never()).moveStep(PatchStep.REMOVE_PROTECTION_TAPE)
    }

    /** With it switched off the site step is skipped, and the tape comes next instead. */
    @Test
    fun removeNeedleCap_skipsSiteLocationWhenThatStepIsHidden() {
        whenever(viewModel.showSiteLocationStep).thenReturn(false)
        show { RemoveNeedleCapStep(viewModel) }

        compose.onNodeWithText(next).performClick()

        verify(viewModel).moveStep(PatchStep.REMOVE_PROTECTION_TAPE)
        verify(viewModel, never()).moveStep(PatchStep.SITE_LOCATION)
    }

    @Test
    fun removeNeedleCap_discardChecksTheConnectionFirst() {
        whenever(viewModel.showSiteLocationStep).thenReturn(false)
        show { RemoveNeedleCapStep(viewModel) }

        compose.onNodeWithText(discard).performClick()

        verify(viewModel).discardPatchWithCommCheck()
    }

    /** Showing the step must not move the wizard on its own - only the button may. */
    @Test
    fun removeNeedleCap_movesNowhereUntilTheButtonIsPressed() {
        whenever(viewModel.showSiteLocationStep).thenReturn(true)

        show { RemoveNeedleCapStep(viewModel) }

        verify(viewModel, never()).moveStep(any())
    }

    // ---- the steps with a single target ----

    /** This step's button is labelled with what it starts, not with a plain "next". */
    @Test
    fun removeProtectionTape_goesToTheSafetyCheck() {
        show { RemoveProtectionTapeStep(viewModel) }

        compose.onNodeWithText(context.getString(R.string.patch_start_safety_check)).performClick()

        verify(viewModel).moveStep(PatchStep.SAFETY_CHECK)
    }

    @Test
    fun turningOffAlarm_confirmsRatherThanNavigating() {
        show { TurningOffAlarmStep(viewModel) }

        compose.onNodeWithText(next).performClick()

        verify(viewModel).onConfirm()
        verify(viewModel, never()).moveStep(any())
    }

    /** The discard-complete step ends the wizard, so its button says Confirm rather than Next. */
    @Test
    fun remove_confirmsRatherThanNavigating() {
        show { RemoveStep(viewModel) }

        compose.onNodeWithText(context.getString(CoreUiR.string.confirm)).performClick()

        verify(viewModel).onConfirm()
    }
}
