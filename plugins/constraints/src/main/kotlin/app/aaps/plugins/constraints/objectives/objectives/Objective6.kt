package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.aps.ApsMode
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.utils.T
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
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