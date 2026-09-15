package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric composable test for [BgInfoSection].
 *
 * Pins what the user actually reads off the BG circle: the placeholder when there is no reading,
 * the value / delta / time-ago lines, the opt-out of the time-ago line, and the accessibility
 * description a screen reader speaks (which is built by string concatenation inside the file, so
 * it is the part a move can silently reword).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BgInfoSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun bgInfo(
        bgText: String = "120",
        bgRange: BgRange = BgRange.IN_RANGE,
        isOutdated: Boolean = false,
        trendArrow: TrendArrow? = TrendArrow.FLAT,
        trendDescription: String = "Flat",
        deltaText: String? = "+2"
    ) = BgInfoData(
        bgValue = 120.0,
        bgText = bgText,
        bgRange = bgRange,
        isOutdated = isOutdated,
        timestamp = 1_700_000_000_000L,
        trendArrow = trendArrow,
        trendDescription = trendDescription,
        delta = 2.0,
        deltaText = deltaText,
        shortAvgDelta = null,
        shortAvgDeltaText = null,
        longAvgDelta = null,
        longAvgDeltaText = null
    )

    @Test
    fun showsPlaceholderWhenNoReading() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = null, timeAgoText = "2 min")
            }
        }

        compose.onNodeWithText("---").assertIsDisplayed()
        // The placeholder branch returns early, so the time-ago line must not appear with it.
        compose.onNodeWithText("2 min").assertDoesNotExist()
    }

    @Test
    fun showsValueDeltaAndTimeAgo() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(), timeAgoText = "2 min")
            }
        }

        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("+2").assertIsDisplayed()
        compose.onNodeWithText("2 min").assertIsDisplayed()
    }

    @Test
    fun hidesTimeAgoWhenShowTimeAgoIsFalse() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(), timeAgoText = "2 min", showTimeAgo = false)
            }
        }

        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("2 min").assertDoesNotExist()
    }

    @Test
    fun hidesDeltaLineWhenThereIsNoDelta() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(deltaText = null), timeAgoText = "2 min")
            }
        }

        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("+2").assertDoesNotExist()
    }

    @Test
    fun accessibilityDescriptionListsValueTrendDeltaAndAge() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(), timeAgoText = "2 min")
            }
        }

        compose.onNodeWithContentDescription("BG 120, Flat, delta +2, 2 min ago").assertExists()
    }

    @Test
    fun accessibilityDescriptionSaysOutdated() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(isOutdated = true), timeAgoText = "12 min")
            }
        }

        compose.onNodeWithContentDescription("BG 120, Flat, delta +2, 12 min ago, outdated").assertExists()
    }

    @Test
    fun accessibilityDescriptionOmitsDeltaAndAgeWhenAbsent() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(deltaText = null), timeAgoText = "")
            }
        }

        compose.onNodeWithContentDescription("BG 120, Flat").assertExists()
    }

    @Test
    fun rendersWithoutTrendArrow() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(
                    bgInfo = bgInfo(trendArrow = null, trendDescription = "Unknown", bgRange = BgRange.LOW),
                    timeAgoText = "1 min"
                )
            }
        }

        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithContentDescription("BG 120, Unknown, delta +2, 1 min ago").assertExists()
    }

    @Test
    fun rendersEveryTrendArrowVariant() {
        // The arc indicator maps every TrendArrow to a position + triangle count. The drawing itself
        // is not assertable, but a missing branch would throw, so this pins that all of them draw.
        val arrows = TrendArrow.entries
        compose.setContent {
            MaterialTheme {
                Column {
                    arrows.forEach { arrow ->
                        BgInfoSection(
                            bgInfo = bgInfo(trendArrow = arrow, bgRange = BgRange.HIGH),
                            timeAgoText = "now",
                            showTimeAgo = false
                        )
                    }
                }
            }
        }

        compose.onAllNodesWithText("120").assertCountEquals(arrows.size)
    }
}
