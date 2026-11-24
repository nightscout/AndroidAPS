package app.aaps.ui.compose.overview

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
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

/** Robolectric composable test for [StatusLightsSettingsContent]: renders title + copy action. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StatusLightsSettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val preferences: Preferences = mock()
    private val config: AppConfig = mock()

    private lateinit var statusLightsTitle: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        statusLightsTitle = ctx.getString(CoreUiR.string.statuslights)
    }

    @Test
    fun rendersTitle() {
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences, LocalConfig provides config) {
                MaterialTheme {
                    StatusLightsSettingsContent(
                        settingsDef = PreferenceSubScreenDef(key = "statuslights", title = CoreUiStrings.treatments)
                    )
                }
            }
        }
        compose.onNodeWithText(statusLightsTitle).assertIsDisplayed()
    }

    /**
     * The settings content scrolls by itself, and the title is its header (#5564). Put back into a scrolling
     * column of its own, a scroll inside a scroll, this crashes with an infinite height in a short window.
     */
    @Test
    @Config(qualifiers = "w360dp-h300dp")
    fun anOpenedGroupCanBeScrolledToItsLastRowInAShortWindow() {
        val switches = listOf(
            BooleanKey.AlertMissedBgReading,
            BooleanKey.AlertPumpUnreachable,
            BooleanKey.AlertIncreaseVolume,
            BooleanKey.OverviewShowTreatmentButton,
            BooleanKey.OverviewShowWizardButton,
            BooleanKey.OverviewShowInsulinButton,
            BooleanKey.OverviewShowCarbsButton,
            BooleanKey.OverviewShowNotesInDialogs
        )
        // Rows for another platform are not drawn
        whenever(config.platform).thenReturn(AppPlatform.Android)
        val lastGroup = RuntimeEnvironment.getApplication().getString(CoreUiR.string.cannula)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences, LocalConfig provides config) {
                MaterialTheme {
                    StatusLightsSettingsContent(
                        settingsDef = PreferenceSubScreenDef(
                            key = "statuslights",
                            title = CoreUiStrings.treatments,
                            items = listOf(
                                PreferenceSubScreenDef(key = "first", title = CoreUiStrings.treatments, items = switches.take(4)),
                                PreferenceSubScreenDef(key = "last", titleResId = CoreUiR.string.cannula, items = switches.drop(4))
                            )
                        )
                    )
                }
            }
        }

        compose.onNodeWithText(lastGroup).performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()

        compose.onAllNodes(isToggleable()).onLast().assertIsOn()
        // The title scrolls with the rows, as before
        compose.onNodeWithText(statusLightsTitle).assertExists()
    }
}
