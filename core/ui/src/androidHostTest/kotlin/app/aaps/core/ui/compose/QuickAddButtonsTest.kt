package app.aaps.core.ui.compose

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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QuickAddButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersOneButtonPerPositiveIncrement_withCorrectLabels() {
        compose.setContent {
            MaterialTheme {
                QuickAddButtons(increment1 = 5, increment2 = 10, increment3 = 20, onAddCarbs = {})
            }
        }

        compose.onNodeWithText("+5").assertIsDisplayed()
        compose.onNodeWithText("+10").assertIsDisplayed()
        compose.onNodeWithText("+20").assertIsDisplayed()
    }

    @Test
    fun filtersOutNonPositiveIncrements() {
        compose.setContent {
            MaterialTheme {
                QuickAddButtons(increment1 = 0, increment2 = 10, increment3 = -5, onAddCarbs = {})
            }
        }

        compose.onNodeWithText("+10").assertIsDisplayed()
        compose.onNodeWithText("+0").assertDoesNotExist()
        compose.onNodeWithText("+-5").assertDoesNotExist()
    }

    @Test
    fun rendersNothing_whenNoPositiveIncrements() {
        compose.setContent {
            MaterialTheme {
                QuickAddButtons(increment1 = 0, increment2 = 0, increment3 = -1, onAddCarbs = {})
            }
        }

        compose.onNodeWithText("+0").assertDoesNotExist()
        compose.onNodeWithText("+-1").assertDoesNotExist()
    }

    @Test
    fun click_firesCallback_withThatIncrement() {
        var added = -1
        compose.setContent {
            MaterialTheme {
                QuickAddButtons(increment1 = 5, increment2 = 10, increment3 = 20, onAddCarbs = { added = it })
            }
        }

        compose.onNodeWithText("+10").performClick()

        assertThat(added).isEqualTo(10)
    }
}
