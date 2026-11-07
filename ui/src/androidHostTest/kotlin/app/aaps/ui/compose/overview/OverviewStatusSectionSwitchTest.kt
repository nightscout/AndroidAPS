package app.aaps.ui.compose.overview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.UiStringIds
import app.aaps.ui.compose.overview.statusLights.StatusItem
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Switch button of the second sensor row in [OverviewStatusSection]: when it is shown, that it
 * asks before it switches, and that the result is shown.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverviewStatusSectionSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    // What MainApp does at startup. Without it the ui strings render as their raw names, and this
    // class passed only when another test class had registered them first.
    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    private var promoted = 0
    private var dismissed = 0

    private fun setSection(canPromote: Boolean, commandsAllowed: Boolean, promotionMessage: String? = null) {
        compose.setContent {
            MaterialTheme {
                OverviewStatusSection(
                    sensorStatus = StatusItem(label = "Sensor", age = "9d4h", icon = Icons.Default.Circle),
                    insulinStatus = null,
                    cannulaStatus = null,
                    batteryStatus = null,
                    secondSensorStatus = StatusItem(
                        label = "New sensor", age = "Ready to switch", ageStatus = StatusLevel.NORMAL,
                        level = "Readings: 72", icon = Icons.Default.Circle, compactLevel = false
                    ),
                    canPromoteSecondSensor = canPromote,
                    onPromoteSecondSensor = { promoted++ },
                    promotionMessage = promotionMessage,
                    onDismissPromotionMessage = { dismissed++ },
                    showFill = false,
                    showPumpBatteryChange = false,
                    commandsAllowed = commandsAllowed,
                    onNavigate = {},
                    statusLightsDef = PreferenceSubScreenDef(key = "test", titleResId = 0),
                    expanded = true,
                    onExpandedChange = {}
                )
            }
        }
    }

    @Test
    fun noSwitchWhileTheSecondSensorIsNotReady() {
        setSection(canPromote = false, commandsAllowed = true)

        compose.onNodeWithText("Ready to switch").assertIsDisplayed()
        compose.onAllNodes(hasText("Switch")).assertCountEquals(0)
    }

    @Test
    fun noSwitchWhenCommandsAreNotAllowed() {
        setSection(canPromote = true, commandsAllowed = false)

        compose.onNodeWithText("Ready to switch").assertIsDisplayed()
        compose.onAllNodes(hasText("Switch")).assertCountEquals(0)
    }

    @Test
    fun theSwitchIsConfirmedBeforeItIsDone() {
        setSection(canPromote = true, commandsAllowed = true)

        compose.onNodeWithText("Switch").performClick()
        // Nothing happens before the user confirms.
        assertThat(promoted).isEqualTo(0)
        compose.onNodeWithText("Use the new sensor for the loop from now on?", substring = true).assertIsDisplayed()

        compose.onNodeWithText("OK").performClick()
        assertThat(promoted).isEqualTo(1)
    }

    @Test
    fun cancelDoesNotSwitch() {
        setSection(canPromote = true, commandsAllowed = true)

        compose.onNodeWithText("Switch").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertThat(promoted).isEqualTo(0)
        compose.onAllNodes(hasText("Use the new sensor for the loop from now on?", substring = true)).assertCountEquals(0)
    }

    @Test
    fun theResultIsShownUntilTheUserClosesIt() {
        setSection(canPromote = false, commandsAllowed = true, promotionMessage = "The loop is busy right now.")

        compose.onNodeWithText("The loop is busy right now.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()

        assertThat(dismissed).isEqualTo(1)
    }
}
