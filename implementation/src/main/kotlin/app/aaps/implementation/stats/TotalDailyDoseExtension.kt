package app.aaps.implementation.stats

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import app.aaps.core.data.model.TDD
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.implementation.R

val TDD.total
    get() = if (totalAmount > 0) totalAmount else basalAmount + bolusAmount

val TDD.basalPct: Double
    get() = if (total > 0) basalAmount / total * 100 else 0.0

fun TDD.Companion.toTableRowHeader(context: Context, rh: ResourceHelper, includeCarbs: Boolean): TableRow =
    TableRow(context).also { header ->
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
        header.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
        header.gravity = Gravity.CENTER_HORIZONTAL
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0; weight = 1f }; text = rh.gs(app.aaps.core.ui.R.string.date) })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1; weight = 1f }; text = "∑" })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2; weight = 1f }; text = rh.gs(app.aaps.core.ui.R.string.bolus) })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3; weight = 1f }; text = rh.gs(app.aaps.core.ui.R.string.basal) })
        header.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4; weight = 1f }; text = rh.gs(app.aaps.core.ui.R.string.basalpct) })
        if (includeCarbs)
            header.addView(TextView(context).apply { layoutParams = lp.apply { column = 5; weight = 1f }; text = rh.gs(R.string.carbs_short) })
    }

fun TDD.toTableRow(context: Context, rh: ResourceHelper, dateUtil: DateUtil, includeCarbs: Boolean): TableRow =
    TableRow(context).also { row ->
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        if ((total.isNaN() || bolusAmount.isNaN() || basalAmount.isNaN() || carbs.isNaN()).not()) {
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0 }; text = dateUtil.dateStringShort(timestamp) })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1 }; text =
                rh.gs(app.aaps.core.ui.R.string.format_insulin_units1, total)
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2 }; text =
                rh.gs(app.aaps.core.ui.R.string.format_insulin_units1, bolusAmount)
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3 }; text =
                rh.gs(app.aaps.core.ui.R.string.format_insulin_units1, basalAmount)
            })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4 }; text = rh.gs(app.aaps.core.ui.R.string.formatPercent, basalPct) })
            if (includeCarbs)
                row.addView(TextView(context).apply {
                    gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 5 }; text = rh.gs(
                    app.aaps.core.objects.R.string.format_carbs, carbs
                        .toInt()
                )
                })
        }
    }

@SuppressLint("SetTextI18n")
fun TDD.toTableRow(context: Context, rh: ResourceHelper, days: Int, includeCarbs: Boolean): TableRow =
    TableRow(context).also { row ->
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
        if ((total.isNaN() || bolusAmount.isNaN() || basalAmount.isNaN() || carbs.isNaN()).not()) {
            row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 0 }; text =
                "%02d".format(days) + " " + rh.gs(app.aaps.core.interfaces.R.string.days)
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 1 }; text =
                rh.gs(app.aaps.core.ui.R.string.format_insulin_units1, total)
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 2 }; text =
                rh.gs(app.aaps.core.ui.R.string.format_insulin_units1, bolusAmount)
            })
            row.addView(TextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 3 }; text =
                rh.gs(app.aaps.core.ui.R.string.format_insulin_units1, basalAmount)
            })
            row.addView(TextView(context).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 4 }; text = rh.gs(app.aaps.core.ui.R.string.formatPercent, basalPct) })
            if (includeCarbs)
                row.addView(TextView(context).apply {
                    gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp.apply { column = 5 }; text = rh.gs(
                    app.aaps.core.objects.R.string.format_carbs, carbs
                        .toInt()
                )
                })
        }
    }
