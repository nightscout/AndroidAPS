package app.aaps.pump.eopatch.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers [EopatchPatchScreen], which picks the wizard step to show from the view model's current
 * step.
 *
 * It is one big `when` over [PatchStep], and the failure it can hide is showing the wrong
 * instructions: these steps tell the user what to do to a patch they are attaching to their body, so
 * "remove the needle cap" appearing where "remove the protection tape" belongs is a real problem
 * rather than a cosmetic one. The real view model is driven here rather than a mock, so the step the
 * screen renders is the step the wizard actually believes it is on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EopatchPatchScreenTest : EopatchViewModelTestBase() {

    @get:Rule
    val compose = createComposeRule()

    // JUnit 4 hooks: Robolectric runs these tests on the vintage engine, so the base's setup has to
    // be wired to @Before rather than @BeforeEach.
    @Before
    fun setUp() {
        setUpMocks()
    }

    @After
    fun tearDown() {
        tearDownMocks()
    }

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun showAt(step: PatchStep) {
        val viewModel = sut().also { it.moveStep(step) }
        compose.setContent { MaterialTheme { EopatchPatchScreen(viewModel = viewModel) } }
    }

    @Test
    fun theNeedleCapStepShowsTheNeedleCapInstructions() {
        showAt(PatchStep.REMOVE_NEEDLE_CAP)

        compose.onNodeWithText(context.getString(R.string.patch_remove_needle_cap)).assertIsDisplayed()
    }

    @Test
    fun theProtectionTapeStepShowsTheProtectionTapeInstructions() {
        showAt(PatchStep.REMOVE_PROTECTION_TAPE)

        compose.onNodeWithText(context.getString(R.string.patch_remove_protection_tape)).assertIsDisplayed()
    }

    @Test
    fun theAlarmStepShowsTheAlarmInstructions() {
        showAt(PatchStep.MANUALLY_TURNING_OFF_ALARM)

        compose.onNodeWithText(context.getString(R.string.patch_manually_turning_off_alarm_title)).assertIsDisplayed()
    }

    @Test
    fun theDiscardedStepSaysTheDiscardIsComplete() {
        showAt(PatchStep.DISCARDED)

        compose.onNodeWithText(context.getString(R.string.patch_discard_complete_title)).assertIsDisplayed()
    }

    /**
     * The point of the dispatcher: one step at a time. If the `when` fell through, or the steps were
     * stacked, the user would be told to do two different things at once.
     */
    @Test
    fun onlyTheCurrentStepIsShown() {
        showAt(PatchStep.REMOVE_NEEDLE_CAP)

        compose.onNodeWithText(context.getString(R.string.patch_remove_needle_cap)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.patch_remove_protection_tape)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.patch_manually_turning_off_alarm_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.patch_discard_complete_title)).assertDoesNotExist()
    }
}
