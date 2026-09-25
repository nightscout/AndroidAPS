package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig

/**
 * Robolectric render tests for the Adaptive*PreferenceItem composables (key-driven rows), mirroring
 * their previews. Locals are provided outside [PreviewTheme] (whose own ProvidePreferenceTheme reads
 * LocalPreferences before providing it — fine in IDE inspection mode, needs an outer provider in tests).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AdaptivePreferenceItemsTest {

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
    fun switchItemRenders() {
        render { AdaptiveSwitchPreferenceItem(booleanKey = BooleanKey.OverviewKeepScreenOn) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun stringItemRenders() {
        render { AdaptiveStringPreferenceItem(stringKey = StringKey.GeneralPatientName) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun intItemRenders() {
        render { AdaptiveIntPreferenceItem(intKey = IntKey.OverviewCarbsButtonIncrement1) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun doubleItemRenders() {
        render { AdaptiveDoublePreferenceItem(doubleKey = DoubleKey.OverviewInsulinButtonIncrement1) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun listIntItemRenders() {
        render {
            AdaptiveListIntPreferenceItem(
                intKey = IntKey.OverviewCarbsButtonIncrement1,
                entries = listOf("5g", "10g", "15g", "20g"),
                entryValues = listOf(5, 10, 15, 20)
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun passwordItemRenders() {
        render {
            AdaptivePasswordPreferenceItem(
                stringKey = StringKey.ProtectionSettingsPassword,
                hashPassword = { it },
                onShowMessage = {}
            )
        }
        compose.onRoot().assertIsDisplayed()
    }
}
