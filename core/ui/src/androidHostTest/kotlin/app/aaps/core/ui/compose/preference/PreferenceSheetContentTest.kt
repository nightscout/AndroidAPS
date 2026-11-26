package app.aaps.core.ui.compose.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig
import app.aaps.core.ui.R as CoreUiR

/** Robolectric render test for [PreferenceSheetContent]: renders a preference sub-screen definition. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PreferenceSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val prefs: Preferences = mock()
    private val config: AppConfig = mock()
    private val profileUtil: ProfileUtil = mock()

    private fun render(block: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(
            LocalPreferences provides prefs,
            LocalConfig provides config,
            LocalProfileUtil provides profileUtil
        ) {
            PreviewTheme { block() }
        }
    }

    @Test
    fun rendersSubScreen() {
        render {
            PreferenceSheetContent(
                settingsDef = PreferenceSubScreenDef(key = "sheet", titleResId = CoreUiR.string.treatments)
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    // ---- a window shorter than the rows (#5564) -------------------------------------------------

    /** Eight switches, far more than a 300 dp window holds. The last one is the row the test has to reach. */
    private val switches = listOf(
        BooleanKey.AlertOverrideDoNotDisturb,
        BooleanKey.AlertPumpUnreachable,
        BooleanKey.AlertIncreaseVolume,
        BooleanKey.OverviewShowTreatmentButton,
        BooleanKey.OverviewShowWizardButton,
        BooleanKey.OverviewShowInsulinButton,
        BooleanKey.OverviewShowCarbsButton,
        BooleanKey.OverviewShowNotesInDialogs
    )

    /**
     * A large font, a small screen or landscape. The sheets that put this content straight into a bottom sheet
     * had no scroll, so the last rows were cut off and could not be reached.
     */
    @Test
    @Config(qualifiers = "w360dp-h300dp")
    fun theLastRowCanBeReachedAndChangedInAShortWindow() {
        render {
            PreferenceSheetContent(settingsDef = PreferenceSubScreenDef(key = "sheet", titleResId = CoreUiR.string.treatments, items = switches))
        }

        // The last switch is the last row's: without a scroll there is nothing to scroll it into view with
        compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()

        compose.onAllNodes(isToggleable()).onLast().assertIsOn()
    }

    /** A group opened below the others grows the content past the window, as the status light groups do. */
    @Test
    @Config(qualifiers = "w360dp-h300dp")
    fun anOpenedGroupCanBeScrolledToItsLastRowInAShortWindow() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val lastGroup = ctx.getString(CoreUiR.string.statuslights)
        render {
            PreferenceSheetContent(
                settingsDef = PreferenceSubScreenDef(
                    key = "sheet",
                    titleResId = CoreUiR.string.treatments,
                    items = listOf(
                        PreferenceSubScreenDef(key = "first", titleResId = CoreUiR.string.treatments, items = switches.take(4)),
                        PreferenceSubScreenDef(key = "last", titleResId = CoreUiR.string.statuslights, items = switches.drop(4))
                    )
                )
            )
        }

        compose.onNodeWithText(lastGroup).performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()

        compose.onAllNodes(isToggleable()).onLast().assertIsOn()
    }
}
