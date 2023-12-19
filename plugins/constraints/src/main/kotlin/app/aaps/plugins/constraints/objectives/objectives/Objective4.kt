package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.keys.DoubleKey
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective4(injector: HasAndroidInjector) : Objective(injector, "maxbasal", R.string.objectives_maxbasal_objective, R.string.objectives_maxbasal_gate) {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var aapsLogger: AAPSLogger

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