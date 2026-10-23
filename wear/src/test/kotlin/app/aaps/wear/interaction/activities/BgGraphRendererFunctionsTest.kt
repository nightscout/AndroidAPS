package app.aaps.wear.interaction.activities

import androidx.compose.ui.graphics.Color
import app.aaps.core.interfaces.rx.weardata.EventData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for the pure top-level helper functions in BgGraphRenderer.kt:
 * [bgColor], [formatTtDuration], [ageColor] and [smbSizeFactor].
 *
 * These are `internal`, so the test lives in the same package to reach them and the module's
 * own color constants ([BgLowColor], [BgHighColor], [BgInRangeColor]). They take only Long/String
 * and return an [androidx.compose.ui.graphics.Color] value class, so no mocks, context or logger
 * are required. The graph-drawing extension `DrawScope.renderBgGraph` needs a Canvas and is not
 * unit-testable, so it is intentionally excluded here.
 */
internal class BgGraphRendererFunctionsTest {

    // Orange constant used inside ageColor for the "stale but not old" bucket.
    private val ageWarnColor = Color(0xFFFF9800)

    // --- bgColor -------------------------------------------------------------

    @Test fun bgColorMapsMinusOneToLow() {
        assertThat(bgColor(-1L)).isEqualTo(BgLowColor)
        assertThat(bgColor(-1L)).isEqualTo(Color(0xFFFF0000))
    }

    @Test fun bgColorMapsOneToHigh() {
        assertThat(bgColor(1L)).isEqualTo(BgHighColor)
        assertThat(bgColor(1L)).isEqualTo(Color(0xFFFFFF00))
    }

    @Test fun bgColorMapsEverythingElseToInRange() {
        assertThat(bgColor(0L)).isEqualTo(BgInRangeColor)
        assertThat(bgColor(2L)).isEqualTo(BgInRangeColor)
        assertThat(bgColor(-2L)).isEqualTo(BgInRangeColor)
        assertThat(bgColor(100L)).isEqualTo(BgInRangeColor)
        // 123456789.toInt() == 123456789 (neither -1 nor 1) -> InRange. NB: Long.MAX_VALUE.toInt()
        // is -1, so it maps to Low; that truncation case is asserted in the dedicated test below.
        assertThat(bgColor(123_456_789L)).isEqualTo(BgInRangeColor)
        assertThat(bgColor(Long.MIN_VALUE)).isEqualTo(BgInRangeColor) // toInt() == 0 -> InRange
        assertThat(bgColor(0L)).isEqualTo(Color(0xFF00FF00))
    }

    @Test fun bgColorUsesIntTruncationSoOnlyIntMinusOneAndOneAreSpecial() {
        // toInt() is applied first; a value that only equals -1/1 after Long truncation would
        // wrap, but plain -1L/1L map to the special cases while nearby values do not.
        assertThat(bgColor(-1L)).isEqualTo(BgLowColor)
        assertThat(bgColor(1L)).isEqualTo(BgHighColor)
        // 4294967295 = 0xFFFFFFFF -> toInt() == -1, confirming truncation semantics reach Low.
        assertThat(bgColor(0xFFFFFFFFL)).isEqualTo(BgLowColor)
    }

    // --- formatTtDuration ----------------------------------------------------

    @Test fun formatTtDurationHoursAndMinutes() {
        assertThat(formatTtDuration(90L * 60_000L, "h")).isEqualTo("1h 30'")
        assertThat(formatTtDuration(150L * 60_000L, "h")).isEqualTo("2h 30'")
    }

    @Test fun formatTtDurationWholeHoursOnly() {
        assertThat(formatTtDuration(60L * 60_000L, "h")).isEqualTo("1h")
        assertThat(formatTtDuration(3_600_000L, "h")).isEqualTo("1h")
        assertThat(formatTtDuration(120L * 60_000L, "h")).isEqualTo("2h")
    }

    @Test fun formatTtDurationMinutesOnly() {
        assertThat(formatTtDuration(45L * 60_000L, "h")).isEqualTo("45'")
        assertThat(formatTtDuration(1L * 60_000L, "h")).isEqualTo("1'")
    }

    @Test fun formatTtDurationZeroAndNegativeClampToZeroMinutes() {
        assertThat(formatTtDuration(0L, "h")).isEqualTo("0'")
        // sub-minute rounds down to 0 minutes.
        assertThat(formatTtDuration(59_000L, "h")).isEqualTo("0'")
        // negative durations are coerced to at least 0.
        assertThat(formatTtDuration(-1L, "h")).isEqualTo("0'")
        assertThat(formatTtDuration(-90L * 60_000L, "h")).isEqualTo("0'")
    }

    @Test fun formatTtDurationHonoursHourUnitString() {
        assertThat(formatTtDuration(90L * 60_000L, "hr")).isEqualTo("1hr 30'")
        assertThat(formatTtDuration(60L * 60_000L, "č")).isEqualTo("1č") // 'č'
    }

    // --- ageColor ------------------------------------------------------------

    @Test fun ageColorFreshBelowFourMinutesIsInRange() {
        assertThat(ageColor(0L)).isEqualTo(BgInRangeColor)
        assertThat(ageColor(3L * 60_000L)).isEqualTo(BgInRangeColor)
        // just under 4 minutes
        assertThat(ageColor(4L * 60_000L - 1L)).isEqualTo(BgInRangeColor)
    }

    @Test fun ageColorBetweenFourAndTenMinutesIsWarnOrange() {
        assertThat(ageColor(4L * 60_000L)).isEqualTo(ageWarnColor)
        assertThat(ageColor(9L * 60_000L)).isEqualTo(ageWarnColor)
        // just under 10 minutes
        assertThat(ageColor(10L * 60_000L - 1L)).isEqualTo(ageWarnColor)
    }

    @Test fun ageColorTenMinutesAndOlderIsLow() {
        assertThat(ageColor(10L * 60_000L)).isEqualTo(BgLowColor)
        assertThat(ageColor(11L * 60_000L)).isEqualTo(BgLowColor)
        assertThat(ageColor(60L * 60_000L)).isEqualTo(BgLowColor)
    }

    // --- smbSizeFactor -------------------------------------------------------

    private fun smb(amount: Double) = EventData.TreatmentData.Treatment(date = 0L, bolus = amount, carbs = 0.0, isSMB = true, isValid = true)

    private fun treatments(vararg smbs: Double, smallBelow: Double = -1.0, largeFrom: Double = -1.0) =
        EventData.TreatmentData(arrayListOf(), arrayListOf(), ArrayList(smbs.map { smb(it) }), arrayListOf(), smallBelow, largeFrom)

    // The three factors are private, so the tests read them off a set with one SMB in each class.
    private val factorOf = smbSizeFactor(treatments(1.0, 4.0, 7.0, smallBelow = 3.0, largeFrom = 5.0))
    private val small = factorOf(1.0)
    private val medium = factorOf(4.0)
    private val large = factorOf(7.0)

    @Test fun smbSizeFactorHasThreeSizes() {
        assertThat(small).isLessThan(medium)
        assertThat(medium).isLessThan(large)
    }

    @Test fun smbSizeFactorUsesTheCutsFromThePhone() {
        // The watch has only 0.05 and 0.10, but the day had a 0.30, so the phone's cuts make both small
        val factor = smbSizeFactor(treatments(0.05, 0.10, smallBelow = 0.133, largeFrom = 0.217))

        assertThat(factor(0.05)).isEqualTo(small)
        assertThat(factor(0.10)).isEqualTo(small)
    }

    @Test fun smbSizeFactorWithoutCutsFromThePhoneCutsFromZeroToTheLargestIntoThirds() {
        // 0..6, cuts at 2 and 4: the smallest SMB does not move the cuts
        val factor = smbSizeFactor(treatments(1.0, 4.0, 6.0))

        assertThat(factor(1.9)).isEqualTo(small)
        assertThat(factor(2.0)).isEqualTo(medium)
        assertThat(factor(3.9)).isEqualTo(medium)
        assertThat(factor(4.0)).isEqualTo(large)
    }

    @Test fun smbSizeFactorDrawsTwoNearDosesTheSameSize() {
        // 0.20 and 0.25 are the same dose to a reader: both large, not smallest and largest
        val factor = smbSizeFactor(treatments(0.20, 0.25))

        assertThat(factor(0.20)).isEqualTo(large)
        assertThat(factor(0.25)).isEqualTo(large)
    }

    @Test fun smbSizeFactorWithoutAnySmbIsMedium() {
        assertThat(smbSizeFactor(treatments())(0.5)).isEqualTo(medium)
    }

    @Test fun smbSizeFactorLargestOfTheDayIsLargeAlsoOnItsOwn() {
        assertThat(smbSizeFactor(treatments(0.5))(0.5)).isEqualTo(large)
    }
}
