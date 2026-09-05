package app.aaps.core.interfaces.stats

import androidx.collection.LongSparseArray

/**
 * Calculator interface for Time In Range (TIR) statistics.
 *
 * This interface provides methods for calculating standard Time In Range statistics,
 * which measure the percentage of time blood glucose values fall within, below, or
 * above user-defined target ranges.
 *
 * Unlike Dexcom TIR which uses fixed 5-range categorization, standard TIR uses
 * user-configurable low and high thresholds to create 3 ranges:
 * - Below range: < lowMgdl
 * - In range: lowMgdl to highMgdl
 * - Above range: > highMgdl
 *
 * The calculator supports:
 * - Per-day TIR calculations stored in a LongSparseArray keyed by midnight timestamp
 * - Averaging across multiple days
 * - User-configurable range thresholds
 * - Typical time periods: 7 days or 30 days
 *
 * Common threshold values:
 * - Low: 70 mg/dL (3.9 mmol/L)
 * - High: 180 mg/dL (10.0 mmol/L)
 *
 * Error handling: Glucose values < 39 mg/dL are counted as errors and excluded.
 *
 * @see TIR
 * @see app.aaps.implementation.stats.TirCalculatorImpl
 * @see DexcomTirCalculator
 */
interface TirCalculator {

    /**
     * Calculates Time In Range statistics for multiple days.
     *
     * Retrieves blood glucose readings for the specified number of days and categorizes
     * them into below/in/above range based on the provided thresholds. Results are
     * organized by day, with each day's statistics stored at its midnight timestamp.
     *
     * Time range: From midnight N days ago to current midnight
     *
     * @param days Number of days to calculate (typically 7 or 30)
     * @param lowMgdl Lower threshold in mg/dL (must be >= 39)
     * @param highMgdl Upper threshold in mg/dL (must be > lowMgdl)
     * @return LongSparseArray mapping midnight timestamps to daily TIR statistics
     * @throws RuntimeException if lowMgdl < 39 or lowMgdl > highMgdl
     */
    suspend fun calculate(days: Long, lowMgdl: Double, highMgdl: Double): LongSparseArray<TIR>

    /**
     * Calculates average TIR across multiple days.
     *
     * Aggregates the counts from all provided daily TIR objects to produce
     * a combined TIR representing the average across all days.
     *
     * The resulting TIR:
     * - Uses the date and thresholds from the first day (if available)
     * - Sums all counts (below, inRange, above, error, count) across days
     * - Can be used to calculate overall percentages
     *
     * @param tirs LongSparseArray of daily TIR statistics to average
     * @return TIR object with aggregated counts from all days
     */
    fun averageTIR(tirs: LongSparseArray<TIR>): TIR
}