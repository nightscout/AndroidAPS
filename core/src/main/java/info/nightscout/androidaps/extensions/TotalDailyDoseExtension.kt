package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TotalDailyDose
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper

val TotalDailyDose.total
    get() = if (totalAmount > 0) totalAmount else basalAmount + bolusAmount

val TotalDailyDose.basalPct: Double
    get() = if (total > 0) basalAmount / total * 100 else 0.0

fun TotalDailyDose.toText(rh: ResourceHelper, dateUtil: DateUtil, includeCarbs: Boolean): String =
    if (total.isNaN() || bolusAmount.isNaN() || basalAmount.isNaN() || carbs.isNaN()) ""
    else if (includeCarbs) rh.gs(R.string.tddwithcarbsformat, dateUtil.dateStringShort(timestamp), total, bolusAmount, basalAmount, basalPct, carbs)
    else rh.gs(R.string.tddformat, dateUtil.dateStringShort(timestamp), total, bolusAmount, basalAmount, basalPct)

fun TotalDailyDose.toText(rh: ResourceHelper, days: Int, includeCarbs: Boolean): String =
    if (total.isNaN() || bolusAmount.isNaN() || basalAmount.isNaN() || carbs.isNaN()) ""
    else if (includeCarbs) rh.gs(R.string.tddwithcarbsformat, days.toString() + rh.gs(R.string.days), total, bolusAmount, basalAmount, basalAmount / total * 100, carbs)
    else rh.gs(R.string.tddformat, days.toString() + rh.gs(R.string.days), total, bolusAmount, basalAmount, basalAmount / total * 100)
