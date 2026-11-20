package app.aaps.plugins.source.eversense

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.plugins.source.R
import app.aaps.plugins.source.activities.EversensePage
import app.aaps.plugins.source.activities.EversensePlacementContent
import app.aaps.plugins.source.activities.EversenseStatusContent
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class EversenseNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val preferences: Preferences = mock()
    private val mode = MutableStateFlow("dark")

    private fun show(connected: Boolean = true, onBack: () -> Unit = {}, onConnect: () -> Unit = {}, onDays: (Int) -> Unit = {}, colors: (ColorScheme) -> Unit = {}) {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(mode)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { colors(scheme) }
                    EversensePage(R.string.eversense_status_title, onBack) {
                        EversenseStatusContent(listOf("Status example"), connected, 14, onConnect, {}, {}, onDays)
                    }
                }
            }
        }
    }

    @Test fun statusUsesCentralThemeAndUpdatesWhenThemeChanges() {
        var colors: ColorScheme? = null
        show(colors = { colors = it })
        compose.waitForIdle()
        val darkImage = compose.onRoot().captureToImage()
        val darkBackground = darkImage.toPixelMap()[0, darkImage.height / 2]
        assertEquals(colors!!.background, darkBackground)
        compose.onNodeWithText("Status example").assertIsDisplayed()
        compose.runOnIdle { mode.value = "light" }
        compose.waitForIdle()
        val lightImage = compose.onRoot().captureToImage()
        val lightBackground = lightImage.toPixelMap()[0, lightImage.height / 2]
        assertEquals(colors!!.background, lightBackground)
        assertNotEquals(darkBackground, lightBackground)
    }

    @Test fun backArrowInvokesNavigationAndDisconnectedSyncIsDisabled() {
        var back = false
        var connect = false
        show(connected = false, onBack = { back = true }, onConnect = { connect = true })
        val context = RuntimeEnvironment.getApplication()
        compose.onNodeWithContentDescription(context.getString(CoreR.string.back)).performClick()
        compose.runOnIdle { assertEquals(true, back) }
        compose.onNodeWithText(context.getString(R.string.eversense_information_last_sync)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.eversense_status_connect)).performClick()
        compose.runOnIdle { assertEquals(true, connect) }
    }

    @Test fun syncDaysSelectionKeepsExistingChoicesAndCallback() {
        var selected = 0
        show(onDays = { selected = it })
        compose.onNodeWithText("14").performClick()
        compose.onNodeWithText("30").performClick()
        compose.runOnIdle { assertEquals(30, selected) }
    }

    @Test fun placementShowsLiveValuesAndInstructionsInsideTheSamePage() {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(mode)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    EversensePage(R.string.eversense_placement_title, {}) {
                        EversensePlacementContent("Good", "60%", "Keep position", "Updated now", 4)
                    }
                }
            }
        }
        compose.onNodeWithText("Good").assertIsDisplayed()
        compose.onNodeWithText("60%").assertIsDisplayed()
        compose.onNodeWithText("Keep position").performScrollTo().assertIsDisplayed()
    }
}
