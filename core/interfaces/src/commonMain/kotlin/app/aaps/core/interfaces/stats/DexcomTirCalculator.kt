package app.aaps.core.interfaces.stats

/**
 * Calculator interface for generating Dexcom-style Time In Range (TIR) statistics.
 *
 * This interface is responsible for calculating comprehensive glucose range statistics
 * following Dexcom's methodology over a 14-day period. It analyzes blood glucose readings
 * from the database and categorizes them into 5 ranges (Very Low, Low, In Range, High, Very High).
 *
 * The calculator:
 * - Retrieves glucose readings for the last 14 days
 * - Applies time-of-day aware range thresholds (different for day vs night)
 * - Calculates percentage distributions across all ranges
 * - Computes statistical measures (mean, standard deviation)
 * - Estimates HbA1c from mean glucose values
 *
 * Implementation is typically marked as @Reusable for dependency injection optimization.
 *
 * @see DexcomTIR
 * @see app.aaps.implementation.stats.DexcomTirCalculatorImpl
 */
interface DexcomTirCalculator {

    /**
     * Calculates Dexcom TIR statistics for the last 14 days.
     *
     * Retrieves all blood glucose readings from midnight 14 days ago until current midnight,
     * processes them into the 5 Dexcom range categories, and returns a [DexcomTIR] object
     * containing all calculated statistics.
     *
     * Time ranges:
     * - Start: Midnight 14 days ago
     * - End: Current midnight
     *
     * @return DexcomTIR object containing all calculated statistics and percentages
     */
    suspend fun calculate(): DexcomTIR
}