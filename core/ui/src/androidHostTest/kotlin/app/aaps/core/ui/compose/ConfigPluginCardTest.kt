package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a screen reader hears on a plugin card in Config Builder.
 *
 * The checkbox and radio button on the card are drawn only, so the card itself must say what it is
 * and whether the plugin is on. With only a `selected` state, a plugin that is off was announced
 * with nothing at all, and switching one off with TalkBack seemed to do nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ConfigPluginCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun plugin(enabled: Boolean) = ConfigPluginUiModel(
        id = "id",
        name = "Plugin",
        description = null,
        composeIcon = null,
        isEnabled = enabled,
        canToggle = true,
        showPreferences = false,
        hasContent = false
    )

    private fun show(enabled: Boolean, mode: SelectionMode, onClick: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                ConfigPluginCard(
                    plugin = plugin(enabled),
                    selectionMode = mode,
                    onCardClick = onClick,
                    onSettingsClick = {},
                    onOpenPluginClick = {},
                    modifier = Modifier.testTag("card")
                )
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun multiSelectPluginThatIsOffIsAnUncheckedCheckbox() {
        show(enabled = false, mode = SelectionMode.MULTI_SELECT)

        compose.onNodeWithTag("card").assert(hasRole(Role.Checkbox)).assertIsOff()
    }

    @Test
    fun multiSelectPluginThatIsOnIsACheckedCheckbox() {
        show(enabled = true, mode = SelectionMode.MULTI_SELECT)

        compose.onNodeWithTag("card").assert(hasRole(Role.Checkbox)).assertIsOn()
    }

    @Test
    fun singleSelectPluginIsARadioButton() {
        show(enabled = false, mode = SelectionMode.SINGLE_SELECT)

        compose.onNodeWithTag("card").assert(hasRole(Role.RadioButton)).assertIsNotSelected()
    }

    @Test
    fun selectedSingleSelectPluginIsASelectedRadioButton() {
        show(enabled = true, mode = SelectionMode.SINGLE_SELECT)

        compose.onNodeWithTag("card").assert(hasRole(Role.RadioButton)).assertIsSelected()
    }

    /** A double-tap with TalkBack is a click on the card. */
    @Test
    fun clickingTheCardTogglesThePlugin() {
        var clicks = 0
        show(enabled = true, mode = SelectionMode.MULTI_SELECT, onClick = { clicks++ })

        compose.onNodeWithTag("card").performClick()

        assertThat(clicks).isEqualTo(1)
    }
}
