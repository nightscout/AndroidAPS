package app.aaps.workflow.iob

import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.SMBDefaults
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.Carbs

fun fromCarbs(t: Carbs, isAAPSOrWeighted: Boolean, profileFunction: ProfileFunction, aapsLogger: AAPSLogger, dateUtil: DateUtil, sp: SP): AutosensData.CarbsInPast {
    val time = t.timestamp
    val carbs = t.amount
    val remaining = t.amount
    val min5minCarbImpact: Double
    val profile = profileFunction.getProfile(t.timestamp)
    if (isAAPSOrWeighted && profile != null) {
        val maxAbsorptionHours = sp.getDouble(app.aaps.core.utils.R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME)
        val sens = profile.getIsfMgdl(t.timestamp)
        val ic = profile.getIc(t.timestamp)
        min5minCarbImpact = t.amount / (maxAbsorptionHours * 60 / 5) * sens / ic
        aapsLogger.debug(
            LTag.AUTOSENS,
            """Min 5m carbs impact for ${carbs}g @${dateUtil.dateAndTimeString(t.timestamp)} for ${maxAbsorptionHours}h calculated to $min5minCarbImpact ISF: $sens IC: $ic"""
        )
    } else {
        min5minCarbImpact = sp.getDouble(app.aaps.core.utils.R.string.key_openapsama_min_5m_carbimpact, SMBDefaults.min_5m_carbimpact)
    }
    return AutosensData.CarbsInPast(time, carbs, min5minCarbImpact, remaining)
}
