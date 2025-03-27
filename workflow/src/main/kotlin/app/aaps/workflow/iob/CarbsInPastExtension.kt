package app.aaps.workflow.iob

import app.aaps.core.data.model.CA
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences

fun fromCarbs(
    t: CA,
    isOref1: Boolean,
    isAAPSOrWeighted: Boolean,
    sens: Double,
    ic: Double,
    aapsLogger: AAPSLogger,
    dateUtil: DateUtil,
    preferences: Preferences
): AutosensData.CarbsInPast {
    val time = t.timestamp
    val carbs = t.amount
    val remaining = t.amount
    val min5minCarbImpact: Double
    if (isAAPSOrWeighted) {
        val maxAbsorptionHours = preferences.get(DoubleKey.AbsorptionMaxTime)
        min5minCarbImpact = t.amount / (maxAbsorptionHours * 60 / 5) * sens / ic
        aapsLogger.debug(LTag.AUTOSENS, "Min 5m carbs impact for ${carbs}g @${dateUtil.dateAndTimeString(t.timestamp)} for ${maxAbsorptionHours}h calculated to $min5minCarbImpact ISF: $sens IC: $ic")
    } else {
        min5minCarbImpact = if (isOref1) preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact) else preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)
    }
    return AutosensData.CarbsInPast(time, carbs, min5minCarbImpact, remaining)
}
