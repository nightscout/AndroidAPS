package app.aaps.plugins.main.general.overview

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.TIR
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.ui.TirChartData
import app.aaps.plugins.main.general.overview.ui.TirCombinedScenario
import app.aaps.plugins.main.general.overview.ui.TirScenario
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for TIR (Time In Range) calculations and data preparation
 * Provides common functionality for both overview and history screens
 */
@Singleton
class TirHelper @Inject constructor(
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val tirCalculator: TirCalculator,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger
) {

    /**
     * Get validated TIR range preferences in mg/dL
     * Converts from user's units (mg/dL or mmol/L) to mg/dL for TirCalculator
     * Falls back to defaults if invalid
     */
    private fun getValidatedRanges(): Pair<Double, Double> {
        // Get preferences in user's units (mg/dL or mmol/L)
        val lowInUserUnits = preferences.get(UnitDoubleKey.OverviewLowMark)
        val highInUserUnits = preferences.get(UnitDoubleKey.OverviewHighMark)

        // Convert to mg/dL (TirCalculator expects mg/dL)
        var lowMgdl = profileUtil.convertToMgdl(lowInUserUnits, profileUtil.units)
        var highMgdl = profileUtil.convertToMgdl(highInUserUnits, profileUtil.units)

        // Validate and fallback to defaults if invalid
        if (lowMgdl < 39.0 || lowMgdl > highMgdl) {
            aapsLogger.warn(LTag.UI, "Invalid TIR range preferences: low=$lowMgdl, high=$highMgdl. Using defaults.")
            lowMgdl = 72.0  // Default LOW mark (mg/dL)
            highMgdl = 180.0  // Default HIGH mark (mg/dL)
        }

        return Pair(lowMgdl, highMgdl)
    }

    /**
     * Calculate TIR data for a specific time range with validated preferences
     */
    fun calculateTirForRange(startTime: Long, endTime: Long): TIR {
        val (lowMgdl, highMgdl) = getValidatedRanges()
        return tirCalculator.calculateRange(startTime, endTime, lowMgdl, highMgdl)
    }

    /**
     * Calculate TIR data for today with validated preferences
     */
    fun calculateTirForToday(): TIR {
        val (lowMgdl, highMgdl) = getValidatedRanges()
        return tirCalculator.calculateToday(lowMgdl, highMgdl)
    }

    /**
     * Calculate percentage from count, handling zero total count
     * @param count The count value
     * @param totalCount Total count (denominator)
     * @param scale Optional scaling factor (e.g., elapsedFraction for partial day)
     * @return Percentage (0.0 if totalCount is 0)
     */
    private fun calculatePct(count: Int, totalCount: Int, scale: Double = 1.0): Double =
        if (totalCount > 0) count.toDouble() / totalCount * scale * 100.0 else 0.0

    /**
     * Normalize percentages to ensure they add up to 100% (fix rounding errors).
     * Uses an epsilon for floating point comparisons and handles the zero-sum case.
     * Distributes any remainder to the largest value.
     * @param values Variable number of percentage values
     * @return Normalized array where sum equals 100.0 (within epsilon)
     */
    private fun normalizePercentages(vararg values: Double): DoubleArray {
        val result = values.toList().toDoubleArray()
        val sum = result.sum()
        val eps = 1e-6

        // If there's effectively no data (all zeros) just return as-is
        if (kotlin.math.abs(sum) <= eps) return result

        // If sum already close to 100, return
        if (kotlin.math.abs(sum - 100.0) <= eps) return result

        val remainder = 100.0 - sum
        // Add remainder to the largest value
        val maxIndex = result.indices.maxByOrNull { result[it] } ?: 0
        result[maxIndex] += remainder

        return result
    }

    /**
     * Calculate TIR chart data with scenarios for today (main overview screen)
     * Returns null if not enough data
     */
    fun calculateTodayChartData(): TirChartData? {
        val tirData = calculateTirForToday()
        val titleText = rh.gs(R.string.tir_today)

        // Calculate elapsed time today and percentages for scenarios
        // Handle DST correctly by calculating actual day length
        val now = dateUtil.now()
        val midnight = app.aaps.core.interfaces.utils.MidnightTime.calc(now)
        val nextMidnight = java.time.Instant.ofEpochMilli(midnight)
            .atZone(java.time.ZoneId.systemDefault())
            .plusDays(1)
            .toInstant()
            .toEpochMilli()
        val elapsedMs = now - midnight
        val totalDayMs = nextMidnight - midnight  // Actual day length (handles DST)
        val elapsedFraction = elapsedMs.toDouble() / totalDayMs
        val remainingFraction = 1.0 - elapsedFraction

        // Current counts
        val totalCount = tirData.count
        if (totalCount == 0) return null
        val belowCount = tirData.below
        val inRangeCount = tirData.inRange
        val aboveCount = tirData.above

        // Till now: show actual proportions of time spent in each category (not scaled to full day)
        // This should fill 100% of the available width, representing elapsed time from midnight till now
        val tillNowBelowFull = calculatePct(belowCount, totalCount)
        val tillNowInRangeFull = calculatePct(inRangeCount, totalCount)
        val tillNowAboveFull = calculatePct(aboveCount, totalCount)

        val (tillNowBelow, tillNowInRange, tillNowAbove) = normalizePercentages(
            tillNowBelowFull,
            tillNowInRangeFull,
            tillNowAboveFull
        )

        // Best case: time so far + remaining time fully in range
        val bestBelowFull = calculatePct(belowCount, totalCount, elapsedFraction)
        val bestInRangeFull = calculatePct(inRangeCount, totalCount, elapsedFraction) + remainingFraction * 100.0
        val bestAboveFull = calculatePct(aboveCount, totalCount, elapsedFraction)

        val (bestBelow, bestInRange, bestAbove) = normalizePercentages(
            bestBelowFull,
            bestInRangeFull,
            bestAboveFull
        )

        // Worst case: time so far + remaining time marked as unknown (gray)
        val worstBelowFull = calculatePct(belowCount, totalCount, elapsedFraction)
        val worstInRangeFull = calculatePct(inRangeCount, totalCount, elapsedFraction)
        val worstAboveFull = calculatePct(aboveCount, totalCount, elapsedFraction)
        val worstUnknownFull = remainingFraction * 100.0

        val (worstBelow, worstInRange, worstAbove, worstUnknown) = normalizePercentages(
            worstBelowFull,
            worstInRangeFull,
            worstAboveFull,
            worstUnknownFull
        )

        val tillNowScenario = TirScenario(
            subtitle = rh.gs(R.string.tir_till_now),
            belowPct = tillNowBelow,
            inRangePct = tillNowInRange,
            abovePct = tillNowAbove,
            unknownPct = 0.0  // No unknown for till now - should fill 100% width
        )

        val combinedScenario = TirCombinedScenario(
            subtitle = rh.gs(R.string.tir_worst_best_case),
            belowPct = bestBelow,
            abovePct = bestAbove,
            worstInRangePct = worstInRange,
            bestInRangePct = bestInRange,
            worstTotalMiddlePct = worstInRange + worstUnknown
        )

        return TirChartData(
            title = titleText,
            tillNowScenario = tillNowScenario,
            combinedScenario = combinedScenario,
            totalCount = totalCount
        )
    }
}
