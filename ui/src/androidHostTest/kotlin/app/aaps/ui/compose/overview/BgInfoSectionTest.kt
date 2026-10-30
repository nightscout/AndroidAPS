package app.aaps.ui.compose.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTypography
import app.aaps.core.ui.compose.LocalAapsTypography
import app.aaps.core.ui.compose.aapsTypography
import app.aaps.core.ui.compose.withoutFontPadding
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * Robolectric composable test for [BgInfoSection].
 *
 * Pins what the user actually reads off the BG circle: the placeholder when there is no reading,
 * the value / delta / time-ago lines, the opt-out of the time-ago line, and the accessibility
 * description a screen reader speaks (built from translated pieces in `bgSpokenDescription`, so it
 * is the part a refactor can silently reword).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BgInfoSectionTest {

    @get:Rule
    val compose = createComposeRule()

    // What MainApp does at startup: the spoken description names its strings, and a TextRef.Named
    // resolves through this registry - without it those parts render as their raw names.
    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

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

    /**
     * The time-ago text comes from `DateUtil.minAgo` and already says "ago". The description used to
     * add another, so TalkBack read "1 min ago ago" on the main screen.
     */
    @Test
    fun accessibilityDescriptionListsValueTrendDeltaAndAge() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(), timeAgoText = "2 min ago")
            }
        }

        compose.onNodeWithContentDescription("Glucose: 120, Flat, delta +2, 2 min ago").assertExists()
    }

    @Test
    fun accessibilityDescriptionSaysOutdated() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(isOutdated = true), timeAgoText = "12 min ago")
            }
        }

        compose.onNodeWithContentDescription("Glucose: 120, Flat, delta +2, 12 min ago, old reading").assertExists()
    }

    /** High and low are only the colour of the circle, so they must be said. */
    @Test
    fun accessibilityDescriptionSaysHighAndLow() {
        compose.setContent {
            MaterialTheme {
                Column {
                    BgInfoSection(bgInfo = bgInfo(bgText = "250", bgRange = BgRange.HIGH), timeAgoText = "")
                    BgInfoSection(bgInfo = bgInfo(bgText = "60", bgRange = BgRange.LOW), timeAgoText = "")
                }
            }
        }

        compose.onNodeWithContentDescription("Glucose: 250, high, Flat, delta +2").assertExists()
        compose.onNodeWithContentDescription("Glucose: 60, low, Flat, delta +2").assertExists()
    }

    @Test
    fun accessibilityDescriptionOmitsDeltaAndAgeWhenAbsent() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(bgInfo = bgInfo(deltaText = null), timeAgoText = "")
            }
        }

        compose.onNodeWithContentDescription("Glucose: 120, Flat").assertExists()
    }

    @Test
    fun rendersWithoutTrendArrow() {
        compose.setContent {
            MaterialTheme {
                BgInfoSection(
                    bgInfo = bgInfo(trendArrow = null, trendDescription = "Unknown", bgRange = BgRange.LOW),
                    timeAgoText = "1 min ago"
                )
            }
        }

        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithContentDescription("Glucose: 120, low, Unknown, delta +2, 1 min ago").assertExists()
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

    /**
     * With a large system font the lines grew into the ring: on an iPhone the value was wider than the
     * hole, and on Android phones the "time ago" line ran into the ring from font 1.3. French and
     * Romanian have the longest "time ago" texts. This looks at the drawn pixels, not at the text boxes:
     * a box is taller than its letters, so a box may reach into the ring while the letters do not.
     */
    @Test
    @Config(qualifiers = "xxhdpi")
    fun noTextIsDrawnOnTheRing() {
        showWideValue()
        val problems = mutableListOf<String>()
        for (linear in listOf(false, true)) {
            for (scale in FONT_SCALES) {
                for (text in TIME_AGO_TEXTS) {
                    showAt(scale, text, linear)
                    problems += listOfNotNull(textOnTheRing("font $scale${if (linear) " linear" else ""}, \"$text\""))
                }
            }
        }
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }

    /** The ring does not grow with the font and the value already fills it, so the value keeps its size. */
    @Test
    fun valueKeepsItsSizeWithALargeFont() {
        showWideValue()
        showAt(1f, ENGLISH_TIME_AGO)
        val normal = heightOf(WIDE_VALUE)
        showAt(2f, ENGLISH_TIME_AGO)
        assertThat(heightOf(WIDE_VALUE)).isWithin(1f).of(normal)
    }

    /** The two small lines still follow the system font, as far as the ring has room for them. */
    @Test
    fun smallLinesGrowWithTheFontWhileThereIsRoom() {
        showWideValue()
        showAt(1f, ENGLISH_TIME_AGO)
        val normal = heightOf(DELTA)
        showAt(1.3f, ENGLISH_TIME_AGO)
        assertThat(heightOf(DELTA)).isGreaterThan(normal * 1.15f)
    }

    private var fontScale by mutableFloatStateOf(1f)
    private var linearFont by mutableStateOf(false)
    private var timeAgo by mutableStateOf("")

    /**
     * The widest value there is in mmol/L, with the font and the "time ago" text set by [showAt]. The
     * typography is the one of the app: AapsTheme takes the font padding away. Dark, on black, with no
     * trend arc: then the dim ring is the only thing drawn outside the hole, and text there is bright.
     */
    private fun showWideValue() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme(), typography = Typography().withoutFontPadding()) {
                val typography = aapsTypography()
                CompositionLocalProvider(
                    LocalAapsTypography provides if (linearFont) typography.withSmallLinesTimes(fontScale) else typography,
                    LocalDensity provides Density(LocalDensity.current.density, if (linearFont) 1f else fontScale)
                ) {
                    Box(Modifier.background(Color.Black)) {
                        BgInfoSection(bgInfo = bgInfo(bgText = WIDE_VALUE, deltaText = DELTA, trendArrow = null), timeAgoText = timeAgo)
                    }
                }
            }
        }
    }

    private fun showAt(scale: Float, text: String, linear: Boolean = false) {
        fontScale = scale
        linearFont = linear
        timeAgo = text
        compose.waitForIdle()
    }

    /**
     * The two small lines [scale] times bigger, as iOS makes them with a large font (the value ignores
     * the system font). A test cannot do it with the font scale: Compose on Android makes big text grow
     * less than small text, and the text is measured with its own conversion, not one given here.
     */
    private fun AapsTypography.withSmallLinesTimes(scale: Float) = copy(
        bgSecondary = bgSecondary.copy(fontSize = bgSecondary.fontSize * scale, lineHeight = bgSecondary.lineHeight * scale),
        bgTimeAgo = bgTimeAgo.copy(fontSize = bgTimeAgo.fontSize * scale, lineHeight = bgTimeAgo.lineHeight * scale)
    )

    /** Height of the [text] line as drawn, so a line drawn smaller than it is laid out counts as smaller. */
    private fun heightOf(text: String): Float =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height

    /**
     * Counts the pixels of text outside the hole. On black the ring is dim (30 % of the BG colour) and
     * the text is bright, so a bright pixel there is text.
     */
    private fun textOnTheRing(where: String): String? {
        val image = compose.onNode(hasContentDescription("Glucose", substring = true)).captureToImage().toPixelMap()
        val hole = with(compose.density) { (AapsSpacing.bgCircleSize / 2 - AapsSpacing.bgRingStrokeWidth).toPx() }
        val centerX = image.width / 2f
        val centerY = image.height / 2f
        var pixels = 0
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val pixel = image[x, y]
                if (hypot(x + 0.5f - centerX, y + 0.5f - centerY) > hole && maxOf(pixel.red, pixel.green, pixel.blue) > BRIGHT) pixels++
            }
        }
        return if (pixels == 0) null else "$where: $pixels pixels of text outside the hole"
    }

    companion object {

        private const val WIDE_VALUE = "14,0"
        private const val DELTA = "-0,7"
        private const val ENGLISH_TIME_AGO = "12m ago"

        /** English, Czech, French and Romanian (the longest) from `minago` */
        private val TIME_AGO_TEXTS = listOf(ENGLISH_TIME_AGO, "před 12m", "il y a 12m", "12 minute în urmă")

        /** Android goes up to 2.0, iOS further */
        private val FONT_SCALES = listOf(1f, 1.3f, 2f, 3f)

        /** The text is 0.8 (grey) or 1.0 (green) bright, the ring 0.3. An edge pixel half over the ring is about 0.55. */
        private const val BRIGHT = 0.6f
    }
}
