package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
import info.nightscout.shared.utils.T

class Objective11(injector: HasAndroidInjector) : Objective(injector, "dyn_isf", R.string.objectives_dyn_isf_objective, R.string.objectives_dyn_isf_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(28).msecs())
                .learned(Learned(R.string.objectives_dyn_isf_learned))
        )
    }
}