package app.aaps.core.ui.compose.preference

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig

/**
 * A row forced by hardened mode is hidden while hardened mode is on: it would be a switch that does
 * nothing. The WebSocket row stands for all of them (`defaultedByHardenedMode`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HardenedModeVisibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val prefs: Preferences = mock()
    private val config: AppConfig = mock()

    @Before
    fun setUp() {
        whenever(config.platform).thenReturn(AppPlatform.Android)
    }

    // Not PreviewTheme: it provides its own fake Preferences over these, and the test needs its own.
    private fun render(block: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalPreferences provides prefs, LocalConfig provides config) {
            MaterialTheme { ProvidePreferenceTheme { block() } }
        }
    }

    @Test
    fun forcedRowIsHiddenInHardenedMode() {
        whenever(prefs.hardenedMode).thenReturn(true)

        render { AdaptiveSwitchPreferenceItem(booleanKey = BooleanKey.NsClient3UseWs) }

        compose.onNodeWithText("Use websockets").assertDoesNotExist()
    }

    @Test
    fun forcedRowIsShownOutsideHardenedMode() {
        whenever(prefs.hardenedMode).thenReturn(false)

        render { AdaptiveSwitchPreferenceItem(booleanKey = BooleanKey.NsClient3UseWs) }

        compose.onNodeWithText("Use websockets").assertIsDisplayed()
    }
}
