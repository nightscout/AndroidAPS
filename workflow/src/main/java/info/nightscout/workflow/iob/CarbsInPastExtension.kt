package info.nightscout.workflow.iob

import info.nightscout.database.entities.Carbs
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.aps.AutosensData
import info.nightscout.interfaces.aps.SMBDefaults
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil

fun fromCarbs(t: Carbs, isAAPSOrWeighted: Boolean, profileFunction: ProfileFunction, aapsLogger: AAPSLogger, dateUtil: DateUtil, sp: SP): AutosensData.CarbsInPast {
    val time = t.timestamp
    val carbs = t.amount
    val remaining = t.amount
    val min5minCarbImpact: Double
    val profile = profileFunction.getProfile(t.timestamp)
    if (isAAPSOrWeighted && profile != null) {
        val maxAbsorptionHours = sp.getDouble(info.nightscout.core.utils.R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME)
        val sens = profile.getIsfMgdl(t.timestamp)
        val ic = profile.getIc(t.timestamp)
        min5minCarbImpact = t.amount / (maxAbsorptionHours * 60 / 5) * sens / ic
        aapsLogger.debug(
            LTag.AUTOSENS,
            """Min 5m carbs impact for ${carbs}g @${dateUtil.dateAndTimeString(t.timestamp)} for ${maxAbsorptionHours}h calculated to $min5minCarbImpact ISF: $sens IC: $ic"""
        )
    } else {
        min5minCarbImpact = sp.getDouble(info.nightscout.core.utils.R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact)
    }
    return AutosensData.CarbsInPast(time, carbs, min5minCarbImpact, remaining)
}
