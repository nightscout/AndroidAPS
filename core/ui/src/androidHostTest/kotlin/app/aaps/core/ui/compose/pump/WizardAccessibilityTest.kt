package app.aaps.core.ui.compose.pump

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a screen reader gets from the shared pump wizard pieces, used by 11 pairing and activation
 * flows. Before: the step dots said nothing, a busy button was "Button, disabled" with no name, and
 * nothing told the user that a wait was over and Next could be pressed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

    @Test
    fun stepDotsSayWhichStepOfHowMany() {
        compose.setContent {
            MaterialTheme { StepProgressIndicator(totalSteps = 7, currentStep = 2) }
        }

        compose.onNodeWithContentDescription("Step 3 of 7").assertIsDisplayed()
    }

    @Test
    fun busyButtonKeepsItsName() {
        compose.setContent {
            MaterialTheme {
                WizardStepLayout(primaryButton = WizardButton(text = "Fill", onClick = {}, loading = true)) { Text("body") }
            }
        }

        compose.onNodeWithContentDescription("Fill").assertIsDisplayed()
    }

    /** The wait is over when the primary button is usable; that is when it is announced. */
    @Test
    fun usableNextButtonIsAnnounced() {
        compose.setContent {
            MaterialTheme {
                WizardStepLayout(primaryButton = WizardButton(text = "Next", onClick = {})) { Text("body") }
            }
        }

        compose.onAllNodes(hasPaneTitle("Next"), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun disabledOrBusyButtonIsNotAnnounced() {
        compose.setContent {
            MaterialTheme {
                WizardStepLayout(primaryButton = WizardButton(text = "Next", onClick = {}, enabled = false)) { Text("body") }
            }
        }

        compose.onAllNodes(hasPaneTitle("Next"), useUnmergedTree = true).assertCountEquals(0)
    }
}
