package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.LocalDateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProfileCarouselCardTest {

    private val dateUtil: DateUtil = mock()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsProfileName() {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
            MaterialTheme {
                ProfileCarouselCard(
                    profileName = "Weekday Profile",
                    basalSum = 24.0,
                    isActive = false,
                    hasErrors = false,
                    pumpIncompatible = false,
                    activeProfileSwitch = null,
                    nextProfileName = null,
                    formatBasalSum = { "24.0 U" }
                )
            }
            }
        }

        compose.onNodeWithText("Weekday Profile").assertIsDisplayed()
    }

    @Test
    fun showsFormattedBasalSum() {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
            MaterialTheme {
                ProfileCarouselCard(
                    profileName = "Weekday Profile",
                    basalSum = 24.0,
                    isActive = false,
                    hasErrors = false,
                    pumpIncompatible = false,
                    activeProfileSwitch = null,
                    nextProfileName = null,
                    formatBasalSum = { "24.0 U" }
                )
            }
            }
        }

        compose.onNodeWithText("24.0 U", substring = true).assertIsDisplayed()
    }

    @Test
    fun temporaryProfile_showsPercentTimeshiftAndRemainingTime() {
        // Switched 20 min ago for 3 h -> 2 h 40 min left.
        whenever(dateUtil.now()).thenReturn(NOW)
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    ProfileCarouselCard(
                        profileName = "Weekday Profile",
                        basalSum = 24.0,
                        isActive = true,
                        hasErrors = false,
                        pumpIncompatible = false,
                        activeProfileSwitch = switch(percentage = 110, timeshiftHours = 2, durationMinutes = 180),
                        nextProfileName = null,
                        formatBasalSum = { "24.0 U" }
                    )
                }
            }
        }

        compose.onNodeWithText("110% +2 h · 2 h 40 min").assertIsDisplayed()
    }

    @Test
    fun temporaryProfile_withoutPercentOrTimeshift_stillShowsRemainingTime() {
        // Before, this case had only the progress bar and no text at all.
        whenever(dateUtil.now()).thenReturn(NOW)
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    ProfileCarouselCard(
                        profileName = "Weekday Profile",
                        basalSum = 24.0,
                        isActive = true,
                        hasErrors = false,
                        pumpIncompatible = false,
                        activeProfileSwitch = switch(percentage = 100, timeshiftHours = 0, durationMinutes = 180),
                        nextProfileName = null,
                        formatBasalSum = { "24.0 U" }
                    )
                }
            }
        }

        compose.onNodeWithText("2 h 40 min").assertIsDisplayed()
    }

    // --- Long profile names (#5457) ---

    /** The carousel size on a phone, with the real fixed card height. */
    private fun setCardWithName(name: String) {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    Box(Modifier.size(CARD_WIDTH, profileCardHeight(1f))) {
                        ProfileCarouselCard(
                            profileName = name,
                            basalSum = 24.0,
                            isActive = false,
                            hasErrors = false,
                            pumpIncompatible = false,
                            activeProfileSwitch = null,
                            nextProfileName = null,
                            formatBasalSum = { "24.0 U" }
                        )
                    }
                }
            }
        }
    }

    private fun nameLayout(name: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    @Test
    fun shortNameStaysOnOneLine() {
        setCardWithName("Weekday")
        assertThat(nameLayout("Weekday").lineCount).isEqualTo(1)
    }

    @Test
    fun longNameWrapsAndIsNotCut() {
        setCardWithName(LONG_NAME)
        val layout = nameLayout(LONG_NAME)
        assertThat(layout.lineCount).isGreaterThan(1)
        assertThat(layout.isLineEllipsized(layout.lineCount - 1)).isFalse()
    }

    @Test
    fun veryLongNameEndsWithEllipsis() {
        val name = LONG_NAME.repeat(4)
        setCardWithName(name)
        val layout = nameLayout(name)
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(layout.isLineEllipsized(1)).isTrue()
    }

    /** The other lines must never be pushed out of the fixed card, at any font size. */
    @Test
    fun otherLinesStayInsideTheCardAtLargeFont() {
        var fontScale by mutableFloatStateOf(1f)
        whenever(dateUtil.now()).thenReturn(NOW)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDateUtil provides dateUtil,
                LocalDensity provides Density(density.density, fontScale)
            ) {
                MaterialTheme {
                    Box(Modifier.size(CARD_WIDTH, profileCardHeight(fontScale)).testTag(CARD_TAG)) {
                        ProfileCarouselCard(
                            profileName = LONG_NAME.repeat(4),
                            basalSum = 24.0,
                            isActive = true,
                            hasErrors = false,
                            pumpIncompatible = false,
                            activeProfileSwitch = switch(percentage = 110, timeshiftHours = 2, durationMinutes = 180),
                            nextProfileName = null,
                            formatBasalSum = { "24.0 U" }
                        )
                    }
                }
            }
        }

        val problems = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            fontScale = scale
            compose.waitForIdle()
            val card = compose.onNodeWithTag(CARD_TAG).getUnclippedBoundsInRoot()
            for (text in listOf("24.0 U", "110% +2 h · 2 h 40 min")) {
                val bounds = compose.onNodeWithText(text, substring = true).getUnclippedBoundsInRoot()
                if (bounds.top < card.top || bounds.bottom > card.bottom) problems += "font $scale: '$text' $bounds is outside $card"
            }
            val name = compose.onNodeWithText(LONG_NAME.repeat(4)).getUnclippedBoundsInRoot()
            if (name.height < 20.dp * scale) problems += "font $scale: the name has only ${name.height}"
        }
        assertThat(problems).isEmpty()
    }

    /** The case in the issue: a normal active profile has room for two lines of name. */
    @Test
    fun activeProfileLongNameHasTwoLines() {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    Box(Modifier.size(CARD_WIDTH, profileCardHeight(1f))) {
                        ProfileCarouselCard(
                            profileName = LONG_NAME,
                            basalSum = 24.0,
                            isActive = true,
                            hasErrors = false,
                            pumpIncompatible = false,
                            activeProfileSwitch = null,
                            nextProfileName = null,
                            formatBasalSum = { "24.0 U" }
                        )
                    }
                }
            }
        }

        assertThat(nameLayout(LONG_NAME).lineCount).isEqualTo(2)
    }

    private fun switch(percentage: Int, timeshiftHours: Int, durationMinutes: Int) = EPS(
        timestamp = NOW - T.mins(20).msecs(),
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MMOL,
        originalProfileName = "Weekday Profile",
        originalCustomizedName = "Weekday Profile",
        originalTimeshift = T.hours(timeshiftHours.toLong()).msecs(),
        originalPercentage = percentage,
        originalDuration = T.mins(durationMinutes.toLong()).msecs(),
        originalEnd = 0,
        iCfg = ICfg("test", 0, 0)
    )

    companion object {

        private const val NOW = 1_700_000_000_000L
        private const val CARD_TAG = "card"
        private const val LONG_NAME = "New U200 basal - 16-2u per day with a long name"
        private val CARD_WIDTH = 284.dp
    }
}
