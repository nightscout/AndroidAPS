package app.aaps.implementation.stats

import androidx.collection.LongSparseArray
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.stats.TIR
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import dagger.Reusable
import javax.inject.Inject

/**
 * Implementation of Time In Range (TIR) statistics calculator.
 *
 * This class calculates standard Time In Range statistics using user-configurable
 * thresholds. Unlike Dexcom TIR which uses fixed 5-range categorization, this
 * implementation provides a simpler 3-range system (below/in/above target).
 *
 * The calculator:
 * - Retrieves blood glucose readings for the specified time period
 * - Organizes readings by day (keyed by midnight timestamp)
 * - Categorizes each reading into below/in/above range
 * - Provides aggregation across multiple days
 *
 * Categorization logic:
 * - Error: < 39 mg/dL (excluded from statistics)
 * - Below: >= 39 and < lowMgdl
 * - In Range: lowMgdl to highMgdl (inclusive)
 * - Above: > highMgdl
 *
 * Daily organization:
 * Each day's statistics are stored separately in a LongSparseArray, allowing
 * for per-day analysis as well as aggregated multi-day statistics.
 *
 * Validation:
 * - Low threshold must be >= 39 mg/dL
 * - High threshold must be > low threshold
 * - Violations throw RuntimeException
 *
 * This class is marked as @Reusable for efficient dependency injection.
 *
 * @property dateUtil Utility for date/time calculations
 * @property persistenceLayer Database layer for retrieving BG readings
 *
 * @see TirCalculator
 * @see TirImpl
 * @see DexcomTirCalculatorImpl
 */
@Reusable
class TirCalculatorImpl @Inject constructor(
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer
) : TirCalculator {

    /**
     * Calculates Time In Range statistics for the specified number of days.
     *
     * Retrieves all blood glucose readings from midnight N days ago to current midnight,
     * organizes them by day, and categorizes each reading into below/in/above range
     * based on the provided thresholds.
     *
     * Each day's statistics are stored in the result LongSparseArray using the day's
     * midnight timestamp as the key. This allows for both per-day and aggregated analysis.
     *
     * Processing:
     * 1. Validates thresholds (lowMgdl >= 39, lowMgdl < highMgdl)
     * 2. Calculates time range (midnight N days ago to current midnight)
     * 3. Retrieves all BG readings in range
     * 4. For each reading:
     *    - Determines which day it belongs to (midnight timestamp)
     *    - Creates TirImpl for that day if it doesn't exist
     *    - Categorizes reading and updates counts
     *
     * @param days Number of days to include in calculation
     * @param lowMgdl Lower threshold in mg/dL (must be >= 39)
     * @param highMgdl Upper threshold in mg/dL (must be > lowMgdl)
     * @return LongSparseArray mapping midnight timestamps to daily TIR statistics
     * @throws RuntimeException if lowMgdl < 39 or lowMgdl > highMgdl
     */
    override suspend fun calculate(days: Long, lowMgdl: Double, highMgdl: Double): LongSparseArray<TIR> {
        if (lowMgdl < 39) throw RuntimeException("Low below 39")
        if (lowMgdl > highMgdl) throw RuntimeException("Low > High")
        val startTime = MidnightTime.calcDaysBack(days)
        val endTime = MidnightTime.calc(dateUtil.now())

        val bgReadings = persistenceLayer.getBgReadingsDataFromTimeToTime(startTime, endTime, true)
        val result = LongSparseArray<TIR>()
        for (bg in bgReadings) {
            val midnight = MidnightTime.calc(bg.timestamp)
            var tir = result[midnight]
            if (tir == null) {
                tir = TirImpl(midnight, lowMgdl, highMgdl)
                result.append(midnight, tir)
            }
            if (bg.value < 39) tir.error()
            if (bg.value >= 39 && bg.value < lowMgdl) tir.below()
            if (bg.value in lowMgdl..highMgdl) tir.inRange()
            if (bg.value > highMgdl) tir.above()
        }
        return result
    }

    /**
     * Calculates aggregate TIR statistics across multiple days.
     *
     * Sums the counts from all provided daily TIR objects to produce a combined
     * TIR representing totals across all days. The resulting TIR can be used to
     * calculate overall percentages for the entire period.
     *
     * The aggregated TIR:
     * - Uses the date and thresholds from the first TIR (if available)
     * - Falls back to defaults (day 7, 70-180 mg/dL) if no TIRs provided
     * - Sums below, inRange, above, error, and count across all days
     *
     * Usage example:
     * ```
     * val dailyTirs = calculate(7, 70.0, 180.0)
     * val avgTir = averageTIR(dailyTirs)
     * val overallInRangePct = avgTir.belowPct() + avgTir.inRangePct()
     * ```
     *
     * @param tirs LongSparseArray of daily TIR statistics to aggregate
     * @return TIR object with summed counts from all days
     */
    override fun averageTIR(tirs: LongSparseArray<TIR>): TIR {
        val totalTir = if (tirs.size() > 0) {
            TirImpl(tirs.valueAt(0).date, tirs.valueAt(0).lowThreshold, tirs.valueAt(0).highThreshold)
        } else {
            TirImpl(7, 70.0, 180.0)
        }
        for (i in 0 until tirs.size()) {
            val tir = tirs.valueAt(i)
            totalTir.below += tir.below
            totalTir.inRange += tir.inRange
            totalTir.above += tir.above
            totalTir.error += tir.error
            totalTir.count += tir.count
        }
        return totalTir
    }
}