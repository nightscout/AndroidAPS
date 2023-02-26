package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.plugins.constraints.R
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective4(injector: HasAndroidInjector) : Objective(injector, "maxbasal", R.string.objectives_maxbasal_objective, R.string.objectives_maxbasal_gate) {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    init {
        tasks.add(
            object : Task(this, R.string.objectives_maxbasal_gate) {
                override fun isCompleted(): Boolean {
                    val profile = profileFunction.getProfile() ?: return false
                    val maxBasalSet = (activePlugin.activeAPS as Constraints).applyBasalConstraints(Constraint(Constants.REALLYHIGHBASALRATE), profile)
                    val maxDailyBasal = profile.getMaxDailyBasal()
                    return maxBasalSet.value() > 2.8 * maxDailyBasal
                }
            }.learned(Learned(R.string.objectives_maxbasal_learned))
        )
    }
}