package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import javax.inject.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Suppress("SpellCheckingInspection")
// Contributed rather than listed in a graph. ObjectivesPlugin takes List<Objective> and is itself
// contributed to AppScope, so the objectives have to be reachable from the same graph.
@ContributesIntoMap(AppScope::class, binding = binding<Objective>())
@IntKey(4)
@SingleIn(AppScope::class)
class Objective4 @Inject constructor(
    preferences: Preferences,
    rh: ResourceHelper,
    dateUtil: DateUtil,
    private val profileFunction: ProfileFunction
) : Objective(preferences, rh, dateUtil, "maxbasal", R.string.objectives_maxbasal_objective, R.string.objectives_maxbasal_gate) {

    init {
        tasks.add(
            object : Task(this, R.string.objectives_maxbasal) {
                override suspend fun isCompleted(): Boolean {
                    val profile = profileFunction.getProfile() ?: return false
                    val maxBasalSet = preferences.getIfExists(DoubleKey.ApsMaxBasal) ?: 0.0
                    val maxDailyBasal = profile.getMaxDailyBasal()
                    return maxBasalSet > 2.8 * maxDailyBasal || preferences.simpleMode
                }
            }.learned(Learned(R.string.objectives_maxbasal_learned))
        )
    }
}