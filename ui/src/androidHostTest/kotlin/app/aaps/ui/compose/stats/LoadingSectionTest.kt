package app.aaps.ui.compose.stats

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
class LoadingSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTitle() {
        compose.setContent {
            MaterialTheme {
                LoadingSection(title = "Total Daily Dose", message = "Loading data")
            }
        }

        compose.onNodeWithText("Total Daily Dose", substring = true).assertIsDisplayed()
    }

    @Test
    fun showsMessage() {
        compose.setContent {
            MaterialTheme {
                LoadingSection(title = "Total Daily Dose", message = "Loading data")
            }
        }

        compose.onNodeWithText("Loading data", substring = true).assertIsDisplayed()
    }
}
