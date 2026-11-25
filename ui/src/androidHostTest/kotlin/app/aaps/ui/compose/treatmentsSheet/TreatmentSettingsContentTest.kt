package app.aaps.ui.compose.treatmentsSheet

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [TreatmentSettingsContent]: renders + fires back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TreatmentSettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val preferences: Preferences = mock()
    private val config: AppConfig = mock()

    private lateinit var backLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        backLabel = ctx.getString(CoreUiR.string.back)
    }

    @Test
    fun rendersAndFiresBack() {
        var back = false
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences, LocalConfig provides config) {
                MaterialTheme {
                    TreatmentSettingsContent(
                        settingsDef = PreferenceSubScreenDef(key = "treatments", title = CoreUiStrings.treatments),
                        onBack = { back = true }
                    )
                }
            }
        }
        compose.onNodeWithContentDescription(backLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(backLabel).performClick()
        assertThat(back).isTrue()
    }

    /**
     * The settings content scrolls by itself, and the back button is its header (#5564). Put back into a
     * scrolling column of its own, a scroll inside a scroll, this crashes with an infinite height in a short
     * window.
     */
    @Test
    @Config(qualifiers = "w360dp-h300dp")
    fun theLastRowCanBeReachedAndChangedInAShortWindow() {
        val switches = listOf(
            BooleanKey.AlertMissedBgReading,
            BooleanKey.AlertPumpUnreachable,
            BooleanKey.AlertCarbsRequired,
            BooleanKey.OverviewShowTreatmentButton,
            BooleanKey.OverviewShowWizardButton,
            BooleanKey.OverviewShowInsulinButton,
            BooleanKey.OverviewShowCarbsButton,
            BooleanKey.OverviewShowNotesInDialogs
        )
        // Rows for another platform are not drawn
        whenever(config.platform).thenReturn(AppPlatform.Android)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences, LocalConfig provides config) {
                MaterialTheme {
                    TreatmentSettingsContent(
                        settingsDef = PreferenceSubScreenDef(key = "treatments", title = CoreUiStrings.treatments, items = switches),
                        onBack = {}
                    )
                }
            }
        }

        compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()

        compose.onAllNodes(isToggleable()).onLast().assertIsOn()
        // The back button scrolls with the rows, as before
        compose.onNodeWithContentDescription(backLabel).assertExists()
    }
}
