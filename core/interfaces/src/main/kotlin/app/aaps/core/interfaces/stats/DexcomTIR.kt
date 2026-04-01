package app.aaps.core.interfaces.stats

import android.content.Context
import android.widget.TableRow
import android.widget.TextView
import app.aaps.core.interfaces.profile.ProfileUtil

/**
 * Interface for Dexcom-style Time In Range (TIR) statistics.
 *
 * This interface provides comprehensive glucose range analysis following Dexcom's methodology,
 * which divides glucose values into 5 ranges: Very Low, Low, In Range, High, and Very High.
 * It supports both day and night-time range definitions, with different thresholds for
 * the high range during night hours (6 PM - 6 AM).
 *
 * The statistics are calculated over a 14-day period and include:
 * - Percentage of time in each glucose range
 * - Standard deviation of glucose values
 * - Estimated HbA1c based on mean glucose
 *
 * Range definitions (default in mg/dL):
 * - Very Low: < 54 mg/dL (3.0 mmol/L)
 * - Low: 54-70 mg/dL (3.0-3.9 mmol/L)
 * - In Range: 70-180 mg/dL (3.9-10.0 mmol/L) during day, 70-120 mg/dL (3.9-6.7 mmol/L) during night
 * - High: 180-250 mg/dL (10.0-13.9 mmol/L) during day, 120-250 mg/dL (6.7-13.9 mmol/L) during night
 * - Very High: > 250 mg/dL (13.9 mmol/L)
 *
 * @see DexcomTirCalculator
 * @see app.aaps.implementation.stats.DexcomTirImpl
 */
interface DexcomTIR {

    /**
     * Calculates the standard deviation of all glucose values in the dataset.
     *
     * @return Standard deviation in mg/dL, or 0.0 if no data points exist
     */
    fun calculateSD(): Double

    /**
     * Creates an Android TextView displaying the estimated HbA1c.
     *
     * HbA1c is calculated from mean glucose using the formula:
     * HbA1c (%) = (mean + 46.7) / 28.7
     * HbA1c (mmol/mol) = ((mean + 46.7) / 28.7 - 2.15) * 10.929
     *
     * @param context Android context for creating the TextView
     * @return TextView with formatted HbA1c value, or empty if no data
     */
    fun toHbA1cView(context: Context): TextView

    /**
     * Creates an Android TextView displaying the standard deviation.
     *
     * @param context Android context for creating the TextView
     * @param profileUtil Utility for converting glucose values to user's preferred units
     * @return TextView with formatted standard deviation value
     */
    fun toSDView(context: Context, profileUtil: ProfileUtil): TextView

    /**
     * Creates an Android TextView displaying the range headers with threshold values.
     *
     * Shows the 5 glucose ranges with their thresholds for both day and night periods.
     *
     * @param context Android context for creating the TextView
     * @param profileUtil Utility for converting glucose values to user's preferred units
     * @return TextView with formatted range headers
     */
    fun toRangeHeaderView(context: Context, profileUtil: ProfileUtil): TextView

    /**
     * Creates an Android TableRow with column headers for the TIR table.
     *
     * @param context Android context for creating the TableRow
     * @return TableRow with headers: Very Low, Low, In Range, High, Very High
     */
    fun toTableRowHeader(context: Context): TableRow

    /**
     * Creates an Android TableRow with percentage values for each glucose range.
     *
     * @param context Android context for creating the TableRow
     * @return TableRow with formatted percentage values for each range
     */
    fun toTableRow(context: Context): TableRow

    // Data accessors for Compose

    /**
     * Percentage of time spent in the Very Low range (< 54 mg/dL).
     *
     * @return Percentage (0-100), or 0.0 if no data points exist
     */
    fun veryLowPct(): Double

    /**
     * Percentage of time spent in the Low range (54-70 mg/dL).
     *
     * @return Percentage (0-100), or 0.0 if no data points exist
     */
    fun lowPct(): Double

    /**
     * Percentage of time spent in the target range.
     *
     * Calculated as: 100 - veryLowPct - lowPct - highPct - veryHighPct
     *
     * @return Percentage (0-100), or 0.0 if no data points exist
     */
    fun inRangePct(): Double

    /**
     * Percentage of time spent in the High range (180-250 mg/dL day, 120-250 mg/dL night).
     *
     * @return Percentage (0-100), or 0.0 if no data points exist
     */
    fun highPct(): Double

    /**
     * Percentage of time spent in the Very High range (> 250 mg/dL).
     *
     * @return Percentage (0-100), or 0.0 if no data points exist
     */
    fun veryHighPct(): Double

    /**
     * Mean glucose value across all data points.
     *
     * @return Mean glucose in mg/dL
     */
    fun mean(): Double

    /**
     * Total number of valid glucose readings in the dataset.
     *
     * Excludes error readings (values < 39 mg/dL).
     *
     * @return Count of valid readings
     */
    fun count(): Int

    /**
     * Threshold for the Very Low range upper bound.
     *
     * @return Threshold value in mg/dL (default: 54)
     */
    fun veryLowTirMgdl(): Double

    /**
     * Threshold for the Low range upper bound.
     *
     * @return Threshold value in mg/dL (default: 70)
     */
    fun lowTirMgdl(): Double

    /**
     * Threshold for the High range upper bound during daytime (6 AM - 6 PM).
     *
     * @return Threshold value in mg/dL (default: 180)
     */
    fun highTirMgdl(): Double

    /**
     * Threshold for the High range upper bound during nighttime (6 PM - 6 AM).
     *
     * @return Threshold value in mg/dL (default: 120)
     */
    fun highNightTirMgdl(): Double

    /**
     * Threshold for the Very High range lower bound.
     *
     * @return Threshold value in mg/dL (default: 250)
     */
    fun veryHighTirMgdl(): Double
}
