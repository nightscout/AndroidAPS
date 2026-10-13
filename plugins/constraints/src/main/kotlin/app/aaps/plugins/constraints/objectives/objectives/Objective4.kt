package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class Objective4 @Inject constructor(
    preferences: Preferences,
    rh: ResourceHelper,
    dateUtil: DateUtil,
    private val profileFunction: ProfileFunction
) : Objective(preferences, rh, dateUtil, "maxbasal", R.string.objectives_maxbasal_objective, R.string.objectives_maxbasal_gate) {

    init {
        tasks.add(
            object : Task(this, R.string.objectives_maxbasal) {
                override fun isCompleted(): Boolean {
                    val profile = profileFunction.getProfile() ?: return false
                    val maxBasalSet = preferences.getIfExists(DoubleKey.ApsMaxBasal) ?: 0.0
                    val maxDailyBasal = profile.getMaxDailyBasal()
                    return maxBasalSet > 2.8 * maxDailyBasal || preferences.simpleMode
                }
            }.learned(Learned(R.string.objectives_maxbasal_learned))
        )
    }
}