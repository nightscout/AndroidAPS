package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.plugins.constraints.R
import info.nightscout.plugins.constraints.safety.SafetyPlugin
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.utils.T
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective5(injector: HasAndroidInjector) : Objective(injector, "maxiobzero", R.string.objectives_maxiobzero_objective, R.string.objectives_maxiobzero_gate) {

    @Inject lateinit var safetyPlugin: SafetyPlugin
    @Inject lateinit var aapsLogger: AAPSLogger

    init {
        tasks.add(MinimumDurationTask(this, T.days(5).msecs()))
        tasks.add(
            object : Task(this, R.string.closedmodeenabled) {
                override fun isCompleted(): Boolean {
                    val closedLoopEnabled = ConstraintObject(false, aapsLogger)
                    safetyPlugin.isClosedLoopAllowed(closedLoopEnabled)
                    return closedLoopEnabled.value()
                }
            }.learned(Learned(R.string.objectives_maxiobzero_learned))
        )
    }
}