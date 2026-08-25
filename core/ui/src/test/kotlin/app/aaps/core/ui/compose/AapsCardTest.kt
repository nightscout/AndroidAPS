package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class AapsCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersContentSlot_andAppliesModifier() {
        compose.setContent {
            MaterialTheme {
                AapsCard(modifier = Modifier.testTag("card")) {
                    Text("Card body")
                }
            }
        }

        compose.onNodeWithTag("card").assertIsDisplayed()
        compose.onNodeWithText("Card body").assertIsDisplayed()
    }

    @Test
    fun rendersContentSlot_whenSelected() {
        compose.setContent {
            MaterialTheme {
                AapsCard(modifier = Modifier.testTag("card"), selected = true) {
                    Text("Selected body")
                }
            }
        }

        compose.onNodeWithTag("card").assertIsDisplayed()
        compose.onNodeWithText("Selected body").assertIsDisplayed()
    }
}
