package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin
import info.nightscout.androidaps.utils.T
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective6(injector: HasAndroidInjector) : Objective(injector, "maxiob", R.string.objectives_maxiob_objective, R.string.objectives_maxiob_gate) {

    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var safetyPlugin: SafetyPlugin

    init {
        tasks.add(MinimumDurationTask(this, T.days(1).msecs()))
        tasks.add(
            object : Task(this, R.string.closedmodeenabled) {
                override fun isCompleted(): Boolean = sp.getString(R.string.key_aps_mode, "open") == "closed"
            })
        tasks.add(
            object : Task(this, R.string.maxiobset) {

                override fun isCompleted(): Boolean {
                    val maxIOB = constraintChecker.getMaxIOBAllowed().value()
                    return maxIOB > 0
                }
            })
    }
}