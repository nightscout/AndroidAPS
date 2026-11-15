package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SceneBadgeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renders() {
        compose.setContent {
            MaterialTheme {
                SceneBadge(modifier = Modifier.testTag("sceneBadge"))
            }
        }

        compose.onNodeWithTag("sceneBadge").assertIsDisplayed()
    }
}
