package app.aaps.core.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
class TonalIconTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersIcon_whenEnabled() {
        compose.setContent {
            MaterialTheme {
                TonalIcon(icon = Icons.Filled.Add, color = Color.Red, modifier = Modifier.testTag("icon"))
            }
        }

        compose.onNodeWithTag("icon").assertIsDisplayed()
    }

    @Test
    fun rendersIcon_whenDisabled() {
        compose.setContent {
            MaterialTheme {
                TonalIcon(icon = Icons.Filled.Add, color = Color.Red, modifier = Modifier.testTag("icon"), enabled = false)
            }
        }

        compose.onNodeWithTag("icon").assertIsDisplayed()
    }
}
