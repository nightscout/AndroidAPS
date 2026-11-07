package app.aaps.ui.compose.overview.statusLights

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable test for [StatusSectionContent]: renders sensor/insulin/cannula/battery status items. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StatusSectionContentTest {

    @get:Rule
    val compose = createComposeRule()

    // What MainApp does at startup. Without it the ui strings render as their raw names, and this
    // class passed only when another test class had registered them first.
    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    @Test
    fun rendersAllStatusItems() {
        compose.setContent {
            MaterialTheme {
                StatusSectionContent(
                    sensorStatus = StatusItem(
                        label = "Sensor", age = "5d12h", ageStatus = StatusLevel.NORMAL, agePercent = 0.55f,
                        level = "Signal OK", levelStatus = StatusLevel.NORMAL, levelPercent = 0.2f, icon = IcCgmInsert
                    ),
                    insulinStatus = StatusItem(
                        label = "Insulin", age = "2d3h", ageStatus = StatusLevel.WARNING, agePercent = 0.75f,
                        level = "86 U", levelStatus = StatusLevel.NORMAL, levelPercent = -1f, icon = IcPumpCartridge
                    ),
                    cannulaStatus = StatusItem(
                        label = "Cannula", age = "1d18h", ageStatus = StatusLevel.NORMAL, agePercent = 0.6f, icon = IcCannulaChange
                    ),
                    batteryStatus = StatusItem(
                        label = "Battery", age = "14d3h", ageStatus = StatusLevel.CRITICAL, agePercent = 0.95f,
                        level = "12%", levelStatus = StatusLevel.CRITICAL, levelPercent = 0.88f, icon = IcPumpBattery
                    ),
                    onSensorInsertClick = {},
                    onFillClick = {},
                    onInsulinChangeClick = {},
                    onBatteryChangeClick = {}
                )
            }
        }
        compose.onNodeWithText("5d12h").assertIsDisplayed()
        compose.onNodeWithText("86 U").assertIsDisplayed()
        compose.onNodeWithText("12%").assertIsDisplayed()
    }

    @Test
    fun eachRowIsOneSpokenItemCarryingItsNameValuesAndSeverity() {
        // A blind user reported having to swipe three times per row - the name, then the age, then
        // the level - for information that can only be read. Each row is now one merged node, so
        // asserting on the row's severity description must also find the name and both values in it.
        compose.setContent {
            MaterialTheme {
                StatusSectionContent(
                    sensorStatus = null,
                    insulinStatus = null,
                    cannulaStatus = null,
                    batteryStatus = StatusItem(
                        label = "Battery", age = "14d3h", ageStatus = StatusLevel.CRITICAL, agePercent = 0.95f,
                        level = "12%", levelStatus = StatusLevel.CRITICAL, levelPercent = 0.88f, icon = IcPumpBattery
                    ),
                    onBatteryChangeClick = {}
                )
            }
        }

        // One node, found by the severity it speaks, holding the row name and both of its values.
        compose.onNode(hasContentDescription("Critical"))
            .assert(hasText("Battery"))
            .assert(hasText("14d3h"))
            .assert(hasText("12%"))

        // The action button stays a node of its own, or it could no longer be pressed. It is named
        // after the row because the same verb appears on several rows.
        compose.onNode(hasContentDescription("Battery") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun theProgressBarIsNotAFocusStopOfItsOwn() {
        // Merging the row is not enough: a progress bar carries range information, which a screen
        // reader treats as a control worth stopping on even inside a merged parent. On a phone each
        // row read as three stops - the row, then "30 percent, progress bar", then the button - and
        // the middle one says nothing the row has not already said. Asserted on the property rather
        // than on spoken text, because that is what makes it a stop.
        compose.setContent {
            MaterialTheme {
                StatusSectionContent(
                    sensorStatus = null,
                    insulinStatus = null,
                    cannulaStatus = null,
                    batteryStatus = StatusItem(
                        label = "Battery", age = "14d3h", ageStatus = StatusLevel.CRITICAL, agePercent = 0.95f,
                        level = "12%", levelStatus = StatusLevel.CRITICAL, levelPercent = 0.88f, icon = IcPumpBattery
                    ),
                    onBatteryChangeClick = {}
                )
            }
        }

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
    }

    @Test
    fun rendersWarmUpAndSecondSensorRows() {
        compose.setContent {
            MaterialTheme {
                StatusSectionContent(
                    sensorStatus = StatusItem(
                        label = "Sensor", age = "9d4h", ageStatus = StatusLevel.WARNING, agePercent = 0.9f, icon = IcCgmInsert
                    ),
                    insulinStatus = null,
                    cannulaStatus = null,
                    batteryStatus = null,
                    warmUpStatus = StatusItem(
                        label = "Warm-up", age = "23 min", agePercent = 0.24f, icon = IcCgmInsert, compactLevel = false
                    ),
                    secondSensorStatus = StatusItem(
                        label = "New sensor", age = "Ready in 8 h", agePercent = 0.33f, level = "Readings: 12",
                        icon = IcGenericCgm, compactLevel = false
                    )
                )
            }
        }
        compose.onNodeWithText("Warm-up").assertIsDisplayed()
        compose.onNodeWithText("23 min").assertIsDisplayed()
        compose.onNodeWithText("New sensor").assertIsDisplayed()
        compose.onNodeWithText("Ready in 8 h").assertIsDisplayed()
        compose.onNodeWithText("Readings: 12").assertIsDisplayed()
        // Not ready: no Switch button at all.
        compose.onAllNodes(hasText("Switch")).assertCountEquals(0)
    }

    @Test
    fun theSwitchButtonIsShownOnlyWithACallbackAndCallsIt() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                StatusSectionContent(
                    sensorStatus = null,
                    insulinStatus = null,
                    cannulaStatus = null,
                    batteryStatus = null,
                    secondSensorStatus = StatusItem(
                        label = "New sensor", age = "Ready to switch", ageStatus = StatusLevel.NORMAL,
                        level = "Readings: 72", icon = IcGenericCgm, compactLevel = false
                    ),
                    onPromoteSecondSensorClick = { clicks++ }
                )
            }
        }
        // Named after its row, like the other row buttons.
        compose.onNode(hasContentDescription("New sensor") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Switch").performClick()
        assertThat(clicks).isEqualTo(1)
    }
}
