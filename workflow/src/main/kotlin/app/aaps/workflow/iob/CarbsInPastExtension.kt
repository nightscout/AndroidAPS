package app.aaps.workflow.iob

import app.aaps.core.data.model.CA
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.Preferences

fun fromCarbs(t: CA, isAAPSOrWeighted: Boolean, profileFunction: ProfileFunction, aapsLogger: AAPSLogger, dateUtil: DateUtil, preferences: Preferences, config: Config, processedDeviceStatusData: ProcessedDeviceStatusData): AutosensData.CarbsInPast {
    val time = t.timestamp
    val carbs = t.amount
    val remaining = t.amount
    val min5minCarbImpact: Double
    val profile = profileFunction.getProfile(t.timestamp)
    if (isAAPSOrWeighted && profile != null) {
        val maxAbsorptionHours = preferences.get(DoubleKey.AbsorptionMaxTime)
        val sens = profile.getIsfMgdlForCarbs(t.timestamp, "fromCarbs", config, processedDeviceStatusData)
        val ic = profile.getIc(t.timestamp)
        min5minCarbImpact = t.amount / (maxAbsorptionHours * 60 / 5) * sens / ic
        aapsLogger.debug(
            LTag.AUTOSENS,
            """Min 5m carbs impact for ${carbs}g @${dateUtil.dateAndTimeString(t.timestamp)} for ${maxAbsorptionHours}h calculated to $min5minCarbImpact ISF: $sens IC: $ic"""
        )
    } else {
        min5minCarbImpact = preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact)
    }
    return AutosensData.CarbsInPast(time, carbs, min5minCarbImpact, remaining)
}
