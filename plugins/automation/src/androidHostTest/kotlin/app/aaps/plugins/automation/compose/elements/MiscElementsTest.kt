package app.aaps.plugins.automation.compose.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class MiscElementsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inputButtonElement_showsText_andFiresClick() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                InputButtonElement(text = "Run", onClick = { clicks++ })
            }
        }

        compose.onNodeWithText("Run").assertIsDisplayed()
        compose.onNodeWithText("Run").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun inputStringEditor_showsValueAndLabel() {
        compose.setContent {
            MaterialTheme {
                InputStringEditor(value = "hello", onValueChange = {}, label = "Name")
            }
        }

        compose.onNodeWithText("hello").assertIsDisplayed()
        compose.onNodeWithText("Name").assertIsDisplayed()
    }

    @Test
    fun inputStringEditor_typing_firesCallback() {
        var captured = ""
        compose.setContent {
            MaterialTheme {
                InputStringEditor(value = "", onValueChange = { captured = it })
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("abc")

        assertThat(captured).isEqualTo("abc")
    }

    @Test
    fun staticLabelRow_showsLabelAndActions() {
        compose.setContent {
            MaterialTheme {
                StaticLabelRow(label = "Conditions") { Text("action") }
            }
        }

        compose.onNodeWithText("Conditions").assertIsDisplayed()
        compose.onNodeWithText("action").assertIsDisplayed()
    }

    @Test
    fun labelWithElementRow_showsPrePostAndContent() {
        compose.setContent {
            MaterialTheme {
                LabelWithElementRow(textPre = "Before", textPost = "After") { Text("middle") }
            }
        }

        compose.onNodeWithText("Before").assertIsDisplayed()
        compose.onNodeWithText("middle").assertIsDisplayed()
        compose.onNodeWithText("After").assertIsDisplayed()
    }
}
