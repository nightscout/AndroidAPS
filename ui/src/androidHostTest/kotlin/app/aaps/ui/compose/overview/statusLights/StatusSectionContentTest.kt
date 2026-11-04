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
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
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
}
