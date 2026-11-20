package app.aaps.plugins.eversense.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.plugins.eversense.R
import app.aaps.plugins.eversense.placement.EversensePage
import app.aaps.plugins.eversense.placement.EversensePlacementContent
import app.aaps.plugins.eversense.ui.EversenseStatusContent
import app.aaps.plugins.eversense.ui.EversenseStatusViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The status tab of the Eversense plugin screen, and the placement screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class EversenseNavigationTest {

    @get:Rule val compose = createComposeRule()
    private val preferences: Preferences = mock()
    private val mode = MutableStateFlow("dark")
    private val context get() = RuntimeEnvironment.getApplication()

    private fun show(
        connected: Boolean = true,
        onConnect: () -> Unit = {},
        onDays: (Int) -> Unit = {},
        onCalibrate: () -> Unit = {}
    ) {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(mode)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    EversenseStatusContent(
                        state = EversenseStatusViewModel.UiState(lines = listOf("Status example"), connected = connected, syncDays = 14),
                        onConnect = onConnect,
                        onChangeTransmitter = {},
                        onDays = onDays,
                        onSync = {},
                        onCalibrate = onCalibrate,
                        onPlacement = {},
                        onDocumentation = {}
                    )
                }
            }
        }
    }

    @Test fun statusShowsWhatTheTransmitterReports() {
        show()
        compose.onNodeWithText("Status example").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.eversense_status_disconnect)).assertIsDisplayed()
    }

    @Test fun disconnectedSyncIsDisabledAndConnectIsOffered() {
        var connect = false
        show(connected = false, onConnect = { connect = true })
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

    /** Calibration goes through the AAPS calibration dialog, opened from here. */
    @Test fun calibrateOpensTheCalibration() {
        var calibrate = false
        show(onCalibrate = { calibrate = true })
        compose.onNodeWithText(context.getString(R.string.eversense_calibration_action)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, calibrate) }
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
