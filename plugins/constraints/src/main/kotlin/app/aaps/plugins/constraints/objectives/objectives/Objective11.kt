package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.utils.T
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R

class Objective11(injector: HasAndroidInjector) : Objective(injector, "dyn_isf", R.string.objectives_dyn_isf_objective, R.string.objectives_dyn_isf_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(28).msecs())
                .learned(Learned(R.string.objectives_dyn_isf_learned))
        )
    }
}