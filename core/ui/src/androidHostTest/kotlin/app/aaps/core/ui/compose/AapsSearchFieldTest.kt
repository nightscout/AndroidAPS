package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AapsSearchFieldTest {

    private lateinit var clearLabel: String

    @Before
    fun setUp() {
        clearLabel = RuntimeEnvironment.getApplication().getString(R.string.clear)
    }

    @Test
    fun showsPlaceholder_whenQueryEmpty() {
        compose.setContent {
            MaterialTheme {
                AapsSearchField(query = "", onQueryChange = {}, placeholder = "Search here")
            }
        }

        compose.onNodeWithText("Search here").assertIsDisplayed()
    }

    @Test
    fun typing_firesOnQueryChange() {
        var captured = ""
        compose.setContent {
            MaterialTheme {
                AapsSearchField(query = "", onQueryChange = { captured = it }, placeholder = "Search here")
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("abc")

        assertThat(captured).isEqualTo("abc")
    }

    @Test
    fun clearButton_hidden_whenQueryEmpty() {
        compose.setContent {
            MaterialTheme {
                AapsSearchField(query = "", onQueryChange = {}, placeholder = "Search here")
            }
        }

        compose.onNodeWithContentDescription(clearLabel).assertDoesNotExist()
    }

    @Test
    fun clearButton_clearsQuery_whenTapped() {
        compose.setContent {
            MaterialTheme {
                var query by remember { mutableStateOf("hello") }
                AapsSearchField(query = query, onQueryChange = { query = it }, placeholder = "Search here")
            }
        }

        compose.onNodeWithContentDescription(clearLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(clearLabel).performClick()

        // After clearing, the placeholder is shown again and the clear button disappears.
        compose.onNodeWithText("Search here").assertIsDisplayed()
        compose.onNodeWithContentDescription(clearLabel).assertDoesNotExist()
    }

    @get:Rule
    val compose = createComposeRule()
}
