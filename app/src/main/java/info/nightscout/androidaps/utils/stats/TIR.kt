package info.nightscout.androidaps.utils.stats

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper

class TIR(val date: Long, val lowThreshold: Double, val highThreshold: Double) {

    internal var below = 0
    internal var inRange = 0
    internal var above = 0
    internal var error = 0
    internal var count = 0

    fun error() = run { error++ }
    fun below() = run { below++; count++ }
    fun inRange() = run { inRange++; count++ }
    fun above() = run { above++; count++ }

    private fun belowPct() = if (count > 0) below.toDouble() / count * 100.0 else 0.0
    private fun inRangePct() = if (count > 0) 100 - belowPct() - abovePct() else 0.0
    private fun abovePct() = if (count > 0) above.toDouble() / count * 100.0 else 0.0

    companion object {

        fun toTableRowHeader(context: Context, rh: ResourceHelper): TableRow =
            TableRow(context).also { header ->
                val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
                header.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
                header.gravity = Gravity.CENTER_HORIZONTAL
                header.addView(TextView(context).apply { layoutParams = lp.apply { column = 0; weight = 1f }; text = rh.gs(R.string.date) })
                header.addView(TextView(context).apply { layoutParams = lp.apply { column = 1; weight = 1f }; text = rh.gs(R.string.below) })
                header.addView(TextView(context).apply { layoutParams = lp.apply { column = 2; weight = 1f }; text = rh.gs(R.string.in_range) })
                header.addView(TextView(context).apply { layoutParams = lp.apply { column = 3; weight = 1f }; text = rh.gs(R.string.above) })
            }
    }
    
    fun toTableRow(context: Context, rh: ResourceHelper, dateUtil: DateUtil): TableRow =
        TableRow(context).also { row ->
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 0 }; text = dateUtil.dateStringShort(date) })
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 1 }; text = rh.gs(R.string.formatPercent, belowPct()) })
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 2 }; text = rh.gs(R.string.formatPercent, inRangePct()) })
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 3 }; text = rh.gs(R.string.formatPercent, abovePct()) })
        }

    @SuppressLint("SetTextI18n")
    fun toTableRow(context: Context, rh: ResourceHelper, days: Int): TableRow =
        TableRow(context).also { row ->
            val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 0 }; text = "%02d".format(days) + " " + rh.gs(R.string.days) })
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 1 }; text = rh.gs(R.string.formatPercent, belowPct()) })
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 2 }; text = rh.gs(R.string.formatPercent, inRangePct()) })
            row.addView(TextView(context).apply { layoutParams = lp.apply { column = 3 }; text = rh.gs(R.string.formatPercent, abovePct()) })
        }
}
