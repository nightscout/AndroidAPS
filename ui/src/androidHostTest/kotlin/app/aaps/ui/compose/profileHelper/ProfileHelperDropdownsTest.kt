package app.aaps.ui.compose.profileHelper

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.R
import app.aaps.ui.UiStringIds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable tests for the ProfileHelper dropdowns: AvailableProfileContent + ProfileSwitchContent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProfileHelperDropdownsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var availableTitle: String

    @Before
    fun setUp() {
        // MainApp does this in production; a Robolectric test has no MainApp, so a TextRef.Named
        // would have no id to resolve to and the screen would render blank text.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val ctx: Context = RuntimeEnvironment.getApplication()
        availableTitle = ctx.getString(R.string.available_profiles)
    }

    @Test
    fun availableProfile_showsTitleAndSelectedValue() {
        compose.setContent {
            MaterialTheme {
                AvailableProfileContent(profiles = listOf("Profile A", "Profile B"), selectedIndex = 0, onProfileSelected = {})
            }
        }
        compose.onNodeWithText(availableTitle).assertIsDisplayed()
        compose.onNodeWithText("Profile A").assertIsDisplayed()
    }

    @Test
    fun profileSwitch_showsSelectedValue() {
        compose.setContent {
            MaterialTheme {
                ProfileSwitchContent(profileSwitches = listOf("PS1", "PS2"), selectedIndex = 1, onProfileSwitchSelected = {})
            }
        }
        compose.onNodeWithText("PS2").assertIsDisplayed()
    }
}
