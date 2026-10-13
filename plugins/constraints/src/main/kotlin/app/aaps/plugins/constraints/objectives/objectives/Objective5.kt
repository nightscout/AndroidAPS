package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class Objective5 @Inject constructor(
    preferences: Preferences,
    rh: ResourceHelper,
    dateUtil: DateUtil,
) : Objective(preferences, rh, dateUtil, "maxiobzero", R.string.objectives_maxiobzero_objective, R.string.objectives_maxiobzero_gate) {

    init {
        tasks.add(MinimumDurationTask(this, T.days(5).msecs()).learned(Learned(R.string.objectives_maxiobzero_learned)))
    }
}