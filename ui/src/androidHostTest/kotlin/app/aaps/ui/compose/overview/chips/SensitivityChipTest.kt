package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.overview.IsfStep
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SensitivityChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsSensitivityText() {
        compose.setContent {
            MaterialTheme {
                SensitivityChip(
                    state = SensitivityUiState(asText = "112%", ratio = 1.12, isEnabled = true, hasData = true),
                    onClick = {}
                )
            }
        }

        compose.onNodeWithText("112%").assertIsDisplayed()
    }

    @Test
    fun showsIsfRange() {
        compose.setContent {
            MaterialTheme {
                SensitivityChip(
                    state = SensitivityUiState(isfFrom = "5.5", isfTo = "6.8", isfStep = IsfStep.UP, ratio = 1.12, isEnabled = true, hasData = true),
                    onClick = {}
                )
            }
        }

        compose.onNodeWithText("5.5").assertIsDisplayed()
        compose.onNodeWithText("6.8").assertIsDisplayed()
        // The arrow says what the step MEANS, so a screen reader hears the direction and not the
        // name of the glyph. The step is given, never read back out of the two texts above.
        compose.onNodeWithContentDescription("Up").assertIsDisplayed()
    }

    @Test
    fun equalIsfValuesGetNoArrowDescription() {
        compose.setContent {
            MaterialTheme {
                SensitivityChip(
                    state = SensitivityUiState(isfFrom = "5.5", isfTo = "5.5", isfStep = IsfStep.SAME, ratio = 1.0, isEnabled = true, hasData = true),
                    onClick = {}
                )
            }
        }

        compose.onAllNodesWithContentDescription("Up").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Down").assertCountEquals(0)
    }

    @Test
    fun click_firesCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                SensitivityChip(
                    state = SensitivityUiState(asText = "88%", ratio = 0.88, isEnabled = false, hasData = true),
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("sensChip")
                )
            }
        }

        compose.onNodeWithTag("sensChip").performClick()

        assertThat(clicks).isEqualTo(1)
    }
}
