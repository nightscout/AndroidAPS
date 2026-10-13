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

@Reusable
class DexcomTirImpl : DexcomTIR {

    private var veryLow = 0
    private var low = 0
    private var inRange = 0
    private var high = 0
    private var veryHigh = 0
    private var error = 0
    private var count = 0

    private var sum = 0.0
    val values = mutableListOf<Double>()

    private val veryLowTirMgdl = Constants.STATS_RANGE_VERY_LOW_MMOL * Constants.MMOLL_TO_MGDL
    private val lowTirMgdl = Constants.STATS_RANGE_LOW_MMOL * Constants.MMOLL_TO_MGDL
    private val highTirMgdl = Constants.STATS_RANGE_HIGH_MMOL * Constants.MMOLL_TO_MGDL
    private val highNightTirMgdl = Constants.STATS_RANGE_HIGH_NIGHT_MMOL * Constants.MMOLL_TO_MGDL
    private val veryHighTirMgdl = Constants.STATS_RANGE_VERY_HIGH_MMOL * Constants.MMOLL_TO_MGDL

    private fun error() = run { error++ }
    private fun veryLow(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; veryLow++; count++ }
    private fun low(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; low++; count++ }
    private fun inRange(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; inRange++; count++ }
    private fun high(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; high++; count++ }
    private fun veryHigh(valueMgdl: Double) = run { values.add(valueMgdl); sum += valueMgdl; veryHigh++; count++ }

    private fun highTirMgdl(hour: Int) = if (hour in 6..22) highTirMgdl else highNightTirMgdl

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

    private fun veryLowPct() = if (count > 0) veryLow.toDouble() / count * 100.0 else 0.0
    private fun lowPct() = if (count > 0) low.toDouble() / count * 100.0 else 0.0
    private fun inRangePct() = if (count > 0) 100 - veryLowPct() - lowPct() - highPct() - veryHighPct() else 0.0
    private fun highPct() = if (count > 0) high.toDouble() / count * 100.0 else 0.0
    private fun veryHighPct() = if (count > 0) veryHigh.toDouble() / count * 100.0 else 0.0
    private fun mean() = sum / count

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
