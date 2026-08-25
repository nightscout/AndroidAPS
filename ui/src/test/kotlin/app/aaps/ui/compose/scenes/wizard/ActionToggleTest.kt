package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isToggleable
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
class ActionToggleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsLabel_andReflectsCheckedState() {
        compose.setContent {
            MaterialTheme {
                ActionToggle(label = "Enable SMB", checked = true, onCheckedChange = {})
            }
        }

        compose.onNodeWithText("Enable SMB").assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun reflectsUncheckedState() {
        compose.setContent {
            MaterialTheme {
                ActionToggle(label = "Enable SMB", checked = false, onCheckedChange = {})
            }
        }

        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun toggle_firesCallback_withNewValue() {
        var newValue: Boolean? = null
        compose.setContent {
            MaterialTheme {
                ActionToggle(label = "Enable SMB", checked = false, onCheckedChange = { newValue = it })
            }
        }

        compose.onNode(isToggleable()).performClick()

        assertThat(newValue).isEqualTo(true)
    }
}
