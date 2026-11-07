package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
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

/**
 * A label and a switch must be ONE control for a screen reader, named by the label. The pattern it
 * replaced - a clickable row beside a Switch with its own handler - was two stops, and the switch
 * one had no name ("Off, switch") in the bolus, carbs and fill dialogs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LabeledSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun labelAndSwitchAreOneNamedSwitch() {
        compose.setContent {
            MaterialTheme {
                var checked by remember { mutableStateOf(false) }
                LabeledSwitch(label = "Start eating soon TT", checked = checked, onCheckedChange = { checked = it })
            }
        }

        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText("Start eating soon TT")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun disabledSwitchDoesNotToggle() {
        var changes = 0
        compose.setContent {
            MaterialTheme {
                LabeledSwitch(label = "Record only", checked = true, onCheckedChange = { changes++ }, enabled = false)
            }
        }

        compose.onNodeWithText("Record only").performClick().assertIsOn()
        assertThat(changes).isEqualTo(0)
    }
}
