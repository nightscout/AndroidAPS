package app.aaps.plugins.automation.compose.elements

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
class DropdownElementsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onOffEditor_showsBothOptions() {
        compose.setContent {
            MaterialTheme {
                InputDropdownOnOffEditor(on = true, onValueChange = {})
            }
        }

        compose.onNodeWithText("On").assertIsDisplayed()
        compose.onNodeWithText("Off").assertIsDisplayed()
    }

    @Test
    fun onOffEditor_selectOff_firesFalse() {
        var captured: Boolean? = null
        compose.setContent {
            MaterialTheme {
                InputDropdownOnOffEditor(on = true, onValueChange = { captured = it })
            }
        }

        compose.onNodeWithText("Off").performClick()

        assertThat(captured).isEqualTo(false)
    }

    @Test
    fun automationDropdown_showsSelectedValueAndLabel() {
        compose.setContent {
            MaterialTheme {
                AutomationDropdown(
                    value = "Profile A",
                    options = listOf("Profile A", "Profile B"),
                    onValueChange = {},
                    label = "Profile"
                )
            }
        }

        compose.onNodeWithText("Profile A").assertIsDisplayed()
        compose.onNodeWithText("Profile").assertIsDisplayed()
    }
}
