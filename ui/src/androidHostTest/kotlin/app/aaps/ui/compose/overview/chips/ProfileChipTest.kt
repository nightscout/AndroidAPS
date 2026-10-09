package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
class ProfileChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsProfileName() {
        compose.setContent {
            MaterialTheme {
                ProfileChip(profileName = "Default 5.6", isModified = false, progress = 0f, onClick = {})
            }
        }

        compose.onNodeWithText("Default 5.6").assertIsDisplayed()
    }

    // A temporary switch reads "name (110%) (2h 40')"; the chip has a fixed height, so a second line was
    // cut off. The text must stay one line: it shrinks a few steps first, then gets an ellipsis.
    private val longName = "My quite long profile name (110%) (2h 40')"

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    fun longName_shrinksToFitOnOneLine() {
        // Too wide for bodyMedium at this width, but it fits after shrinking: no ellipsis.
        compose.setContent {
            MaterialTheme {
                ProfileChip(profileName = longName, isModified = true, progress = 0.3f, onClick = {}, modifier = Modifier.width(320.dp))
            }
        }

        compose.onNodeWithText(longName).assertIsDisplayed()
        val layout = layoutOf(longName)
        assertThat(layout.lineCount).isEqualTo(1)
        assertThat(layout.isLineEllipsized(0)).isFalse()
    }

    @Test
    fun longName_inNarrowChip_staysOnOneLine() {
        // Even where no shrink step is enough, never a second line.
        compose.setContent {
            MaterialTheme {
                ProfileChip(profileName = longName, isModified = true, progress = 0.3f, onClick = {}, modifier = Modifier.width(160.dp))
            }
        }

        assertThat(layoutOf(longName).lineCount).isEqualTo(1)
    }

    @Test
    fun click_firesCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ProfileChip(
                    profileName = "Default 5.6",
                    isModified = true,
                    progress = 0.6f,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("profileChip")
                )
            }
        }

        compose.onNodeWithTag("profileChip").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun disabled_doesNotFireCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ProfileChip(
                    profileName = "Default 5.6",
                    isModified = false,
                    progress = 0f,
                    onClick = { clicks++ },
                    enabled = false,
                    modifier = Modifier.testTag("profileChip")
                )
            }
        }

        compose.onNodeWithTag("profileChip").performClick()

        assertThat(clicks).isEqualTo(0)
    }
}
