package app.aaps.core.ui.compose.preference

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.configuration.Config as AppConfig
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric render tests for the base preference composables, mirroring their previews. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PreferenceComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    private val prefs: Preferences = mock()
    private val config: AppConfig = mock()

    private fun render(block: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalPreferences provides prefs, LocalConfig provides config) {
            PreviewTheme { block() }
        }
    }

    @Test
    fun switchPreference_rendersTitleAndSummary() {
        render {
            SwitchPreference(
                value = true,
                onValueChange = {},
                title = { Text("Enable feature") },
                summary = { Text("Toggle this feature on or off") }
            )
        }
        compose.onNodeWithText("Enable feature").assertIsDisplayed()
    }

    @Test
    fun preferenceCategory_rendersTitle() {
        render { PreferenceCategory(title = { Text("General") }) }
        compose.onNodeWithText("General").assertIsDisplayed()
    }

    @Test
    fun textFieldPreference_rendersTitle() {
        render {
            TextFieldPreference(
                value = "Hello",
                onValueChange = {},
                title = { Text("Username") },
                textToValue = { it },
                summary = { Text("Hello") }
            )
        }
        compose.onNodeWithText("Username").assertIsDisplayed()
    }

    @Test
    fun listPreference_rendersTitle() {
        render {
            ListPreference(
                value = "Option B",
                onValueChange = {},
                values = listOf("Option A", "Option B", "Option C"),
                title = { Text("Choose option") },
                summary = { Text("Option B") }
            )
        }
        compose.onNodeWithText("Choose option").assertIsDisplayed()
    }

    @Test
    fun preference_rendersTitleAndFiresClick() {
        var clicked = false
        render {
            Preference(
                title = { Text("Preference title") },
                summary = { Text("Summary text") },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = { clicked = true }
            )
        }
        compose.onNodeWithText("Preference title").assertIsDisplayed()
        compose.onNodeWithText("Preference title").performClick()
        assertThat(clicked).isTrue()
    }
}
