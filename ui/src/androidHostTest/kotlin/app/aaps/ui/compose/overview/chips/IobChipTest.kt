package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class IobChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsIobText() {
        compose.setContent {
            MaterialTheme {
                IobChip(state = IobUiState(text = "1.25 U", iobTotal = 1.25), onClick = {})
            }
        }

        compose.onNodeWithText("1.25 U").assertIsDisplayed()
    }

    @Test
    fun showsIobText_zeroValue() {
        compose.setContent {
            MaterialTheme {
                IobChip(state = IobUiState(text = "0.00 U", iobTotal = 0.0), onClick = {})
            }
        }

        compose.onNodeWithText("0.00 U").assertIsDisplayed()
    }

    @Test
    fun click_firesCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                IobChip(
                    state = IobUiState(text = "1.25 U", iobTotal = 1.25),
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("iobChip")
                )
            }
        }

        compose.onNodeWithTag("iobChip").performClick()

        assertThat(clicks).isEqualTo(1)
    }
}
