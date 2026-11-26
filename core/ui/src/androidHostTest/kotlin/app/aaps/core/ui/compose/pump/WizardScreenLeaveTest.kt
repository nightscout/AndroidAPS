package app.aaps.core.ui.compose.pump

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** How a step's Cancel leaves the wizard, with and without the question first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardScreenLeaveTest {

    @get:Rule
    val compose = createComposeRule()

    private var left = 0

    private fun render(askBeforeLeaving: Boolean) = compose.setContent {
        MaterialTheme {
            WizardScreen(
                currentStep = "step",
                totalSteps = 1,
                currentStepIndex = 0,
                canGoBack = true,
                onBack = { left++ },
                cancelDialogTitle = QUESTION_TITLE,
                cancelDialogText = "Are you sure?",
                askBeforeLeaving = askBeforeLeaving
            ) { _, onCancel ->
                Button(onClick = onCancel) { Text(CANCEL) }
            }
        }
    }

    @Test
    fun cancelAsksFirstByDefault() {
        render(askBeforeLeaving = true)

        compose.onNodeWithText(CANCEL).performClick()

        compose.onNodeWithText(QUESTION_TITLE).assertIsDisplayed()
        assertThat(left).isEqualTo(0)
    }

    /** A step where leaving loses nothing, as the first screen of a Medtrum deactivation. */
    @Test
    fun cancelLeavesAtOnceWhenTheStepAsksNothing() {
        render(askBeforeLeaving = false)

        compose.onNodeWithText(CANCEL).performClick()

        compose.onNodeWithText(QUESTION_TITLE).assertDoesNotExist()
        assertThat(left).isEqualTo(1)
    }

    private companion object {

        const val CANCEL = "Cancel step"
        const val QUESTION_TITLE = "Leave the wizard"
    }
}
