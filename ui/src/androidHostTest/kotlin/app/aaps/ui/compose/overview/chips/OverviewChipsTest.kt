package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
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

/** Robolectric composable tests for the overview status chips: render the hoisted state text and fire
 *  the click callback — headless JVM, no emulator. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverviewChipsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun iobChipRendersTextAndFiresClick() {
        var clicked = false
        compose.setContent {
            MaterialTheme { IobChip(state = IobUiState(text = "1.5U"), onClick = { clicked = true }) }
        }
        compose.onNodeWithText("1.5U").assertIsDisplayed()
        compose.onNodeWithText("1.5U").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun cobChipRendersText() {
        compose.setContent {
            MaterialTheme { CobChip(state = CobUiState(text = "20g")) }
        }
        compose.onNodeWithText("20g").assertIsDisplayed()
    }

    @Test
    fun sensitivityChipRendersTextAndFiresClick() {
        var clicked = false
        compose.setContent {
            MaterialTheme { SensitivityChip(state = SensitivityUiState(asText = "100%"), onClick = { clicked = true }) }
        }
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("100%").performClick()
        assertThat(clicked).isTrue()
    }
}
