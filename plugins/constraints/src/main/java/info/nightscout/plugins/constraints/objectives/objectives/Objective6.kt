package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.ApsMode
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.plugins.constraints.R
import info.nightscout.shared.utils.T
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective6(injector: HasAndroidInjector) : Objective(injector, "maxiob", R.string.objectives_maxiob_objective, R.string.objectives_maxiob_gate) {

    @Inject lateinit var constraintChecker: ConstraintsChecker

    init {
        tasks.add(MinimumDurationTask(this, T.days(1).msecs()))
        tasks.add(
            object : Task(this, R.string.closedmodeenabled) {
                override fun isCompleted(): Boolean = ApsMode.fromString(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)) == ApsMode.CLOSED
            })
        tasks.add(
            object : Task(this, R.string.maxiobset) {

                override fun isCompleted(): Boolean {
                    val maxIOB = constraintChecker.getMaxIOBAllowed().value()
                    return maxIOB > 0
                }
            }.learned(Learned(R.string.objectives_maxiob_learned))
        )
    }
}