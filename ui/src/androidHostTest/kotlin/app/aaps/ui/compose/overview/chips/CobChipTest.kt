package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CobChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCobText() {
        compose.setContent {
            MaterialTheme {
                CobChip(state = CobUiState(text = "24g", cobValue = 24.0))
            }
        }

        compose.onNodeWithText("24g").assertIsDisplayed()
    }

    @Test
    fun showsCobText_zeroValue() {
        compose.setContent {
            MaterialTheme {
                CobChip(state = CobUiState(text = "0g", cobValue = 0.0))
            }
        }

        compose.onNodeWithText("0g").assertIsDisplayed()
    }
}
