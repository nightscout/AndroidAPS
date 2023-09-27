package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.utils.T
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector

class Objective10(injector: HasAndroidInjector) : Objective(injector, "auto", R.string.objectives_auto_objective, R.string.objectives_auto_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(28).msecs())
                .learned(Learned(R.string.objectives_autosens_learned))
        )
    }
}