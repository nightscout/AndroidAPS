package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TotalDailyDose
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*

val TotalDailyDose.total
    get() = if (totalAmount > 0) totalAmount else basalAmount + bolusAmount

fun TotalDailyDose.toText(resourceHelper: ResourceHelper, dateUtil: DateUtil, includeCarbs: Boolean): String =
    if (includeCarbs) resourceHelper.gs(R.string.tddwithcarbsformat, dateUtil.dateStringShort(timestamp), total, bolusAmount, basalAmount, basalAmount / total * 100, carbs)
    else resourceHelper.gs(R.string.tddformat, dateUtil.dateStringShort(timestamp), total, bolusAmount, basalAmount, basalAmount / total * 100)

fun TotalDailyDose.toText(resourceHelper: ResourceHelper, days: Int, includeCarbs: Boolean): String =
    if (includeCarbs) resourceHelper.gs(R.string.tddwithcarbsformat, String.format(Locale.getDefault(), "%d ", days) + resourceHelper.gs(R.string.days), total, bolusAmount, basalAmount, basalAmount / total * 100, carbs)
    else resourceHelper.gs(R.string.tddformat, String.format(Locale.getDefault(), "%d ", days) + resourceHelper.gs(R.string.days), total, bolusAmount, basalAmount, basalAmount / total * 100)
