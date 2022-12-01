package info.nightscout.implementation.stats

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.database.entities.TotalDailyDose
import info.nightscout.implementation.R
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil

val TotalDailyDose.total
    get() = if (totalAmount > 0) totalAmount else basalAmount + bolusAmount

val TotalDailyDose.basalPct: Double
    get() = if (total > 0) basalAmount / total * 100 else 0.0

fun TotalDailyDose.Companion.toTableRowHeader(context: Context, rh: ResourceHelper, includeCarbs: Boolean): TableRow =
    TableRow(context).also { header ->
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
        header.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
        header.gravity = Gravity.CENTER_HORIZONTAL
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0; weight = 1f }; text = rh.gs(R.string.date) })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1; weight = 1f }; text = "∑" })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2; weight = 1f }; text = rh.gs(R.string.bolus) })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3; weight = 1f }; text = rh.gs(R.string.basal) })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4; weight = 1f }; text = rh.gs(R.string.basalpct) })
        if (includeCarbs)
            header.addView(TextView(context).apply { layoutParams = lp.apply { column = 5; weight = 1f }; text = rh.gs(R.string.carbs_short) })
    }

fun TotalDailyDose.toTableRow(context: Context, rh: ResourceHelper, dateUtil: DateUtil, includeCarbs: Boolean): TableRow =
    TableRow(context).also { row ->
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        if ((total.isNaN() || bolusAmount.isNaN() || basalAmount.isNaN() || carbs.isNaN()).not()) {
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0 }; text = dateUtil.dateStringShort(timestamp) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1 }; text = rh.gs(R.string.format_insulin_units1, total) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2 }; text = rh.gs(R.string.format_insulin_units1, bolusAmount) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3 }; text = rh.gs(R.string.format_insulin_units1, basalAmount) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4 }; text = rh.gs(R.string.formatPercent, basalPct) })
            if (includeCarbs)
                row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 5 }; text = rh.gs(R.string.format_carbs, carbs.toInt()) })
        }
    }

@SuppressLint("SetTextI18n")
fun TotalDailyDose.toTableRow(context: Context, rh: ResourceHelper, days: Int, includeCarbs: Boolean): TableRow =
    TableRow(context).also { row ->
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        if ((total.isNaN() || bolusAmount.isNaN() || basalAmount.isNaN() || carbs.isNaN()).not()) {
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0 }; text = "%02d".format(days) + " " + rh.gs(R.string.days) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1 }; text = rh.gs(R.string.format_insulin_units1, total) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2 }; text = rh.gs(R.string.format_insulin_units1, bolusAmount) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3 }; text = rh.gs(R.string.format_insulin_units1, basalAmount) })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4 }; text = rh.gs(R.string.formatPercent, basalPct) })
            if (includeCarbs)
                row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 5 }; text = rh.gs(R.string.format_carbs, carbs.toInt()) })
        }
    }
