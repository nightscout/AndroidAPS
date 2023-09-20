package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.interfaces.constraints.PluginConstraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.plugins.constraints.R
import info.nightscout.rx.logging.AAPSLogger
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective4(injector: HasAndroidInjector) : Objective(injector, "maxbasal", R.string.objectives_maxbasal_objective, R.string.objectives_maxbasal_gate) {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var aapsLogger: AAPSLogger

    init {
        tasks.add(
            object : Task(this, R.string.objectives_maxbasal_gate) {
                override fun isCompleted(): Boolean {
                    val profile = profileFunction.getProfile() ?: return false
                    val maxBasalSet = (activePlugin.activeAPS as PluginConstraints).applyBasalConstraints(ConstraintObject(Double.MAX_VALUE, aapsLogger), profile)
                    val maxDailyBasal = profile.getMaxDailyBasal()
                    return maxBasalSet.value() > 2.8 * maxDailyBasal
                }
            }.learned(Learned(R.string.objectives_maxbasal_learned))
        )
    }
}