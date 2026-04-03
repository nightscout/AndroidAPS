package app.aaps.implementation.stats

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.stats.DexcomTIR
import app.aaps.implementation.R
import dagger.Reusable
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Implementation of Dexcom Time In Range (TIR) statistics accumulator.
 *
 * This class accumulates blood glucose readings and categorizes them into Dexcom's
 * 5-range system: Very Low, Low, In Range, High, and Very High. It maintains counts
 * for each range and calculates various statistical measures.
 *
 * Key features:
 * - Time-of-day aware range thresholds (different high threshold for day vs night)
 * - Statistical calculations (mean, standard deviation)
 * - HbA1c estimation from mean glucose
 * - Both Android View-based and Compose-compatible accessors
 *
 * Range thresholds (from Constants):
 * - Very Low: < 54 mg/dL (3.0 mmol/L)
 * - Low: 54-70 mg/dL (3.0-3.9 mmol/L)
 * - In Range: 70-180 mg/dL during day (6 AM-6 PM), 70-120 mg/dL during night (6 PM-6 AM)
 * - High: 180-250 mg/dL during day, 120-250 mg/dL during night
 * - Very High: > 250 mg/dL (13.9 mmol/L)
 *
 * Error handling: Glucose values < 39 mg/dL are counted as errors and excluded from statistics.
 *
 * This class is stateful and accumulates data via the [add] method. Each instance should
 * be used for a single calculation cycle.
 *
 * @see DexcomTIR
 * @see DexcomTirCalculatorImpl
 */
@Reusable
class DexcomTirImpl : DexcomTIR {

    /** Count of readings in Very Low range (< 54 mg/dL) */
    private var veryLow = 0

    /** Count of readings in Low range (54-70 mg/dL) */
    private var low = 0

    /** Count of readings in target range (70-180/120 mg/dL depending on time) */
    private var inRange = 0

    /** Count of readings in High range (180/120-250 mg/dL depending on time) */
    private var high = 0

    /** Count of readings in Very High range (> 250 mg/dL) */
    private var veryHigh = 0

    /** Count of error readings (< 39 mg/dL) */
    private var error = 0

    /** Total count of valid readings (excludes errors) */
    private var count = 0

    /** Sum of all glucose values for mean calculation */
    private var sum = 0.0

    /** List of all glucose values for standard deviation calculation */
    val values = mutableListOf<Double>()

    /** Very Low threshold in mg/dL (54 mg/dL = 3.0 mmol/L) */
    private val veryLowTirMgdl = Constants.STATS_RANGE_VERY_LOW_MMOL * Constants.MMOLL_TO_MGDL

    /** Low threshold in mg/dL (70 mg/dL = 3.9 mmol/L) */
    private val lowTirMgdl = Constants.STATS_RANGE_LOW_MMOL * Constants.MMOLL_TO_MGDL

    /** High threshold for daytime in mg/dL (180 mg/dL = 10.0 mmol/L) */
    private val highTirMgdl = Constants.STATS_RANGE_HIGH_MMOL * Constants.MMOLL_TO_MGDL

    /** High threshold for nighttime in mg/dL (120 mg/dL = 6.7 mmol/L) */
    private val highNightTirMgdl = Constants.STATS_RANGE_HIGH_NIGHT_MMOL * Constants.MMOLL_TO_MGDL

    /** Very High threshold in mg/dL (250 mg/dL = 13.9 mmol/L) */
    private val veryHighTirMgdl = Constants.STATS_RANGE_VERY_HIGH_MMOL * Constants.MMOLL_TO_MGDL

    /** Increments error count for invalid readings */
    private fun error() = run { error++ }

    /** Records a Very Low glucose reading */
    private fun veryLow(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; veryLow++; count++ }

    /** Records a Low glucose reading */
    private fun low(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; low++; count++ }

    /** Records an In Range glucose reading */
    private fun inRange(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; inRange++; count++ }

    /** Records a High glucose reading */
    private fun high(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; high++; count++ }

    /** Records a Very High glucose reading */
    private fun veryHigh(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; veryHigh++; count++ }

    /**
     * Returns the appropriate high threshold based on time of day.
     *
     * Daytime (6 AM - 10 PM): 180 mg/dL
     * Nighttime (10 PM - 6 AM): 120 mg/dL
     *
     * @param hour Hour of day (0-23)
     * @return High threshold in mg/dL
     */
    private fun highTirMgdl(hour: Int) = if (hour in 6..22) highTirMgdl else highNightTirMgdl

    /**
     * Adds a blood glucose reading to the statistics.
     *
     * The reading is categorized into one of 5 ranges or marked as an error based on:
     * - The glucose value in mg/dL
     * - The time of day (for time-dependent high threshold)
     *
     * Categorization rules:
     * - < 39 mg/dL: Error (not included in statistics)
     * - < 54 mg/dL: Very Low
     * - < 70 mg/dL: Low
     * - > 250 mg/dL: Very High
     * - > high threshold (time-dependent): High
     * - Otherwise: In Range
     *
     * @param time Timestamp of the reading in milliseconds
     * @param valueMgdl Glucose value in mg/dL
     */
    fun add(time: Long, valueMgdl: Double) {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        val hour = c[Calendar.HOUR_OF_DAY]
        when {
            valueMgdl < 39                -> error()
            valueMgdl < veryLowTirMgdl    -> veryLow(valueMgdl)
            valueMgdl < lowTirMgdl        -> low(valueMgdl)
            valueMgdl > veryHighTirMgdl   -> veryHigh(valueMgdl)
            valueMgdl > highTirMgdl(hour) -> high(valueMgdl)
            else                          -> inRange(valueMgdl)
        }
    }

    override fun veryLowPct() = if (count > 0) veryLow.toDouble() / count * 100.0 else 0.0
    override fun lowPct() = if (count > 0) low.toDouble() / count * 100.0 else 0.0
    override fun inRangePct() = if (count > 0) 100 - veryLowPct() - lowPct() - highPct() - veryHighPct() else 0.0
    override fun highPct() = if (count > 0) high.toDouble() / count * 100.0 else 0.0
    override fun veryHighPct() = if (count > 0) veryHigh.toDouble() / count * 100.0 else 0.0
    override fun mean() = sum / count
    override fun count() = count
    override fun veryLowTirMgdl() = veryLowTirMgdl
    override fun lowTirMgdl() = lowTirMgdl
    override fun highTirMgdl() = highTirMgdl
    override fun highNightTirMgdl() = highNightTirMgdl
    override fun veryHighTirMgdl() = veryHighTirMgdl

    override fun calculateSD(): Double {
        if (count == 0) return 0.0
        var standardDeviation = 0.0
        for (num in values) standardDeviation += (num - mean()).pow(2.0)
        return sqrt(standardDeviation / count)
    }

    override fun toHbA1cView(context: Context): TextView =
        TextView(context).apply {
            text =
                if (count == 0) ""
                else context.getString(R.string.hba1c) + " " +
                    (10 * (mean() + 46.7) / 28.7).roundToInt() / 10.0 + "%" +
                    " (" +
                    (((mean() + 46.7) / 28.7 - 2.15) * 10.929).roundToInt() +
                    " mmol/mol)"
            setTypeface(typeface, Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
        }

    @SuppressLint("SetTextI18n")
    override fun toSDView(context: Context, profileUtil: ProfileUtil): TextView =
        TextView(context).apply {
            val sd = calculateSD()
            text = "\n" + context.getString(R.string.std_deviation, profileUtil.fromMgdlToStringInUnits(sd))
            setTypeface(typeface, Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
        }

    override fun toRangeHeaderView(context: Context, profileUtil: ProfileUtil): TextView =
        TextView(context).apply {
            text = StringBuilder()
                .append(context.getString(R.string.detailed_14_days))
                .append("\n")
                .append(context.getString(R.string.day_tir))
                .append(" (")
                .append(profileUtil.fromMgdlToStringInUnits(0.0))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(veryLowTirMgdl))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(lowTirMgdl))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(highTirMgdl))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(veryHighTirMgdl))
                .append("-∞)\n")
                .append(context.getString(R.string.night_tir))
                .append(" (")
                .append(profileUtil.fromMgdlToStringInUnits(0.0))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(veryLowTirMgdl))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(lowTirMgdl))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(highNightTirMgdl))
                .append("-")
                .append(profileUtil.stringInCurrentUnitsDetect(veryHighTirMgdl))
                .append("-∞)\n")
                .toString()
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            setTextAppearance(android.R.style.TextAppearance_Material_Medium)
        }

    override fun toTableRowHeader(context: Context): TableRow =
        TableRow(context).also { header ->
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            header.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            header.gravity = Gravity.CENTER_HORIZONTAL
            header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0; weight = 1f }; text = context.getString(R.string.veryLow) })
            header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1; weight = 1f }; text = context.getString(R.string.low) })
            header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2; weight = 1f }; text = context.getString(R.string.in_range) })
            header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3; weight = 1f }; text = context.getString(R.string.high) })
            header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4; weight = 1f }; text = context.getString(R.string.veryHigh) })
        }

    @SuppressLint("SetTextI18n")
    override fun toTableRow(context: Context): TableRow =
        TableRow(context).also { row ->
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0 }; text =
                context.getString(app.aaps.core.ui.R.string.formatPercent, veryLowPct())
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1 }; text =
                context.getString(app.aaps.core.ui.R.string.formatPercent, lowPct())
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2 }; text =
                context.getString(app.aaps.core.ui.R.string.formatPercent, inRangePct())
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3 }; text =
                context.getString(app.aaps.core.ui.R.string.formatPercent, highPct())
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4 }; text =
                context.getString(app.aaps.core.ui.R.string.formatPercent, veryHighPct())
            })
        }
}
