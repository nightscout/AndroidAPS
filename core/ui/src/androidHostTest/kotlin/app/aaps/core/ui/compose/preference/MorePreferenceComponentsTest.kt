package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig
import app.aaps.core.ui.R as CoreUiR

/** Robolectric render tests for the remaining preference composables (adaptive variants + base widgets). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MorePreferenceComponentsTest {

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
    fun adaptiveUnitDoubleItemRenders() {
        render { AdaptiveUnitDoublePreferenceItem(unitKey = UnitDoubleKey.OverviewLowMark) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun adaptiveMasterPasswordItemRenders() {
        render {
            AdaptiveMasterPasswordPreferenceItem(
                checkPassword = { _, _ -> true },
                hashPassword = { it },
                onShowMessage = {}
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun adaptiveStringListItemRenders() {
        render {
            AdaptiveStringListPreferenceItem(
                stringKey = StringKey.GeneralPatientName,
                entries = mapOf("a" to "Alpha", "b" to "Beta")
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun inlineStringItemRenders() {
        render { InlineStringPreferenceItem(stringKey = StringKey.GeneralPatientName) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun inlineStringListItemRenders() {
        render {
            InlineStringListPreferenceItem(
                stringKey = StringKey.GeneralPatientName,
                entries = mapOf("a" to "Alpha", "b" to "Beta")
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun sliderWithButtonsRenders() {
        render {
            PreferenceSliderWithButtons(
                value = 1.0,
                onValueChange = {},
                valueRange = 0.0..10.0
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun clickableCategoryHeaderRendersTitle() {
        render {
            ClickablePreferenceCategoryHeader(
                title = TextRef.AndroidRes(CoreUiR.string.treatments),
                expanded = false,
                onToggle = {}
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun syncBadgeRendersWhenVisible() {
        render { SyncBadge(visible = true) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun basicPreferenceRendersAndFiresClick() {
        var clicked = false
        render {
            BasicPreference(
                textContainer = { Text("BasicPref") },
                onClick = { clicked = true }
            )
        }
        compose.onNodeWithText("BasicPref").assertIsDisplayed()
        compose.onNodeWithText("BasicPref").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun collapsibleCardShowsContentWhenExpanded() {
        render {
            CollapsibleCardSectionContent(
                title = TextRef.AndroidRes(CoreUiR.string.treatments),
                expanded = true,
                onToggle = {},
                content = { Text("cardbody") }
            )
        }
        compose.onNodeWithText("cardbody").assertIsDisplayed()
    }
}
