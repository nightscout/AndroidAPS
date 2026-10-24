package app.aaps.wear.interaction.activities

import androidx.compose.ui.graphics.Color
import app.aaps.core.interfaces.rx.weardata.EventData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for the pure top-level helper functions in BgGraphRenderer.kt:
 * [bgColor], [formatTtDuration], [ageColor], [smbSizeFactor], [bolusSizeFactor], [carbsSizeFactor],
 * [interpolatedSgvAt] and [markerAnchorTime].
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

    // --- interpolatedSgvAt ---------------------------------------------------

    private val minute = 60_000L

    private fun reading(time: Long, sgv: Double) = EventData.SingleBg(dataset = 0, timeStamp = time, sgv = sgv, high = 180.0, low = 70.0)

    // Readings at 0, 5 and 10 minutes, going 100 -> 120 -> 150
    private val readings = listOf(reading(0, 100.0), reading(5 * minute, 120.0), reading(10 * minute, 150.0))

    @Test fun interpolatedSgvAtBetweenTwoReadingsIsLinear() {
        assertThat(interpolatedSgvAt(2 * minute + 30_000L, readings)).isEqualTo(110f)
        assertThat(interpolatedSgvAt(6 * minute, readings)).isEqualTo(126f)
    }

    @Test fun interpolatedSgvAtExactlyOnAReadingIsThatReading() {
        assertThat(interpolatedSgvAt(5 * minute, readings)).isEqualTo(120f)
    }

    @Test fun interpolatedSgvAtAfterTheLastReadingHoldsItsValueForFifteenMinutes() {
        assertThat(interpolatedSgvAt(25 * minute, readings)).isEqualTo(150f)
        assertThat(interpolatedSgvAt(25 * minute + 1, readings)).isNull()
    }

    @Test fun interpolatedSgvAtBeforeTheFirstReadingHoldsItsValueForFifteenMinutes() {
        assertThat(interpolatedSgvAt(-15 * minute, readings)).isEqualTo(100f)
        assertThat(interpolatedSgvAt(-15 * minute - 1, readings)).isNull()
    }

    @Test fun interpolatedSgvAtInsideASensorGapUsesTheNearSideOnly() {
        // 35 minutes without readings: a marker in the gap takes the reading within 15 min as it is
        // instead of a value between the two, and in the middle, where neither is near, there is none
        val gap = listOf(reading(0, 100.0), reading(5 * minute, 120.0), reading(40 * minute, 200.0))

        assertThat(interpolatedSgvAt(7 * minute, gap)).isEqualTo(120f)
        assertThat(interpolatedSgvAt(20 * minute, gap)).isEqualTo(120f)
        assertThat(interpolatedSgvAt(23 * minute, gap)).isNull()
        assertThat(interpolatedSgvAt(26 * minute, gap)).isEqualTo(200f)
    }

    @Test fun interpolatedSgvAtWithoutReadingsIsNull() {
        assertThat(interpolatedSgvAt(5 * minute, emptyList())).isNull()
    }

    // --- markerAnchorTime ----------------------------------------------------

    @Test fun markerAnchorTimeTakesAnEarlierPartnerWithinFiveMinutes() {
        assertThat(markerAnchorTime(10 * minute, listOf(7 * minute))).isEqualTo(7 * minute)
        assertThat(markerAnchorTime(10 * minute, listOf(5 * minute))).isEqualTo(5 * minute)
    }

    @Test fun markerAnchorTimeKeepsItsOwnTimeWhenThePartnerCameLater() {
        // The later half of the pair takes the earlier one's time, the earlier half keeps its own
        assertThat(markerAnchorTime(10 * minute, listOf(12 * minute))).isEqualTo(10 * minute)
    }

    @Test fun markerAnchorTimeIgnoresAPartnerMoreThanFiveMinutesAway() {
        assertThat(markerAnchorTime(10 * minute, listOf(4 * minute))).isEqualTo(10 * minute)
        assertThat(markerAnchorTime(10 * minute, emptyList())).isEqualTo(10 * minute)
    }

    @Test fun markerAnchorTimeTakesTheNearestOfSeveralPartners() {
        assertThat(markerAnchorTime(10 * minute, listOf(6 * minute, 9 * minute))).isEqualTo(9 * minute)
    }

    // --- bolusSizeFactor / carbsSizeFactor -----------------------------------

    private fun bolus(amount: Double) = EventData.TreatmentData.Treatment(date = 0L, bolus = amount, carbs = 0.0, isSMB = false, isValid = true)
    private fun carbs(amount: Double) = EventData.TreatmentData.Treatment(date = 0L, bolus = 0.0, carbs = amount, isSMB = false, isValid = true)

    private fun meals(
        vararg entries: EventData.TreatmentData.Treatment,
        bolusSmallBelow: Double = -1.0, bolusLargeFrom: Double = -1.0,
        carbsSmallBelow: Double = -1.0, carbsLargeFrom: Double = -1.0
    ) = EventData.TreatmentData(
        arrayListOf(), arrayListOf(), arrayListOf(*entries), arrayListOf(),
        bolusSmallBelow = bolusSmallBelow, bolusLargeFrom = bolusLargeFrom, carbsSmallBelow = carbsSmallBelow, carbsLargeFrom = carbsLargeFrom
    )

    // The meal factors are private too, read off a set with one bolus in each class: 0..9, cuts at 3 and 6
    private val mealFactorOf = bolusSizeFactor(meals(bolus(1.0), bolus(4.0), bolus(9.0)))
    private val mealSmall = mealFactorOf(1.0)
    private val mealMedium = mealFactorOf(4.0)
    private val mealLarge = mealFactorOf(9.0)

    @Test fun smbSizeFactorIsHeldBackFromThePhonesProportions() {
        // 10 : 16 : 22 dp on the phone's IOB graph, less on the shared bottom line here
        assertThat(medium / small).isWithin(0.05f).of(1.4f)
        assertThat(large / small).isWithin(0.05f).of(1.8f)
        // and the largest SMB stays clearly smaller than a large bolus
        assertThat(large).isLessThan(mealLarge)
    }

    @Test fun bolusSizeFactorHasThePhonesProportions() {
        // 12 : 17 : 22 dp on the phone's BG graph
        assertThat(mealSmall).isLessThan(mealMedium)
        assertThat(mealMedium / mealSmall).isWithin(0.05f).of(17f / 12f)
        assertThat(mealLarge / mealSmall).isWithin(0.05f).of(22f / 12f)
    }

    @Test fun bolusSizeFactorCutsFromZeroSoTwoNearDosesAreTheSameSize() {
        val factor = bolusSizeFactor(meals(bolus(0.20), bolus(0.25)))

        assertThat(factor(0.20)).isEqualTo(mealLarge)
        assertThat(factor(0.25)).isEqualTo(mealLarge)
    }

    @Test fun bolusSizeFactorUsesTheCutsFromThePhone() {
        // The watch has 3 U and 5 U, but the day had a 12 U, so the phone's cuts make them small and medium
        val factor = bolusSizeFactor(meals(bolus(3.0), bolus(5.0), bolusSmallBelow = 4.0, bolusLargeFrom = 8.0))

        assertThat(factor(3.0)).isEqualTo(mealSmall)
        assertThat(factor(5.0)).isEqualTo(mealMedium)
    }

    @Test fun bolusSizeFactorLooksAtManualBolusesOnly() {
        // A lone bolus is the day's largest and drawn large, like on the phone; without any it is medium
        assertThat(bolusSizeFactor(meals(bolus(3.0), smb(0.1), carbs(40.0)))(3.0)).isEqualTo(mealLarge)
        assertThat(bolusSizeFactor(meals(smb(0.1), carbs(40.0)))(3.0)).isEqualTo(mealMedium)
    }

    @Test fun carbsSizeFactorMakesAPieceOfExtendedCarbsSmallNextToItsMeal() {
        // A 60 g meal and its 5 g pieces: cuts at 20 and 40
        val factor = carbsSizeFactor(meals(carbs(60.0), carbs(5.0), carbs(5.0)))

        assertThat(factor(5.0)).isEqualTo(mealSmall)
        assertThat(factor(60.0)).isEqualTo(mealLarge)
    }

    @Test fun carbsSizeFactorUsesTheCutsFromThePhone() {
        val factor = carbsSizeFactor(meals(carbs(30.0), carbsSmallBelow = 20.0, carbsLargeFrom = 40.0))

        assertThat(factor(30.0)).isEqualTo(mealMedium)
    }
}
