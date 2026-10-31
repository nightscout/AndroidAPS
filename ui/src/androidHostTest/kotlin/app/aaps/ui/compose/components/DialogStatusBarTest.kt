package app.aaps.ui.compose.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.UiStringIds
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The glucose at the top of the bolus, carbs and insulin dialogs. An old reading is only struck
 * through and high/low is only the colour, so a screen reader user heard "7.2" for a reading they
 * should not dose on. Both must be said.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DialogStatusBarTest {

    @get:Rule
    val compose = createComposeRule()

    // What MainApp does at startup; without it the named UI strings render as their raw names.
    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    private fun bg(isOutdated: Boolean, range: BgRange) = BgInfoData(
        bgValue = 12.4,
        bgText = "12.4",
        bgRange = range,
        isOutdated = isOutdated,
        timestamp = 1_700_000_000_000L,
        trendArrow = TrendArrow.FLAT,
        trendDescription = "Flat",
        delta = 0.1,
        deltaText = "+0.1",
        shortAvgDelta = null,
        shortAvgDeltaText = null,
        longAvgDelta = null,
        longAvgDeltaText = null
    )

    @Test
    fun oldHighReadingIsSpokenAsOldAndHigh() {
        compose.setContent {
            MaterialTheme {
                DialogStatusBar(
                    bgInfo = BgInfoUiState(bgInfo = bg(isOutdated = true, range = BgRange.HIGH), timeAgoText = "25 min ago"),
                    iob = IobUiState(),
                    cob = CobUiState()
                )
            }
        }

        compose.onNodeWithContentDescription("Glucose: 12.4, high, stable, delta +0.1, 25 min ago, old reading", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun currentInRangeReadingHasNoRangeWordAndNoWarning() {
        compose.setContent {
            MaterialTheme {
                DialogStatusBar(
                    bgInfo = BgInfoUiState(bgInfo = bg(isOutdated = false, range = BgRange.IN_RANGE), timeAgoText = "1 min ago"),
                    iob = IobUiState(),
                    cob = CobUiState()
                )
            }
        }

        compose.onNodeWithContentDescription("Glucose: 12.4, stable, delta +0.1, 1 min ago", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
