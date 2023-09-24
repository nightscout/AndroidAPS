package info.nightscout.workflow.iob

import app.aaps.interfaces.aps.AutosensData
import app.aaps.interfaces.aps.SMBDefaults
import app.aaps.interfaces.configuration.Constants
import app.aaps.interfaces.logging.AAPSLogger
import app.aaps.interfaces.logging.LTag
import app.aaps.interfaces.profile.ProfileFunction
import app.aaps.interfaces.sharedPreferences.SP
import app.aaps.interfaces.utils.DateUtil
import info.nightscout.database.entities.Carbs

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
