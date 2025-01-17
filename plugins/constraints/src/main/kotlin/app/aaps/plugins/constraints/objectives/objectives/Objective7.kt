package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.data.time.T
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector

class Objective7(injector: HasAndroidInjector) : Objective(injector, "autosens", R.string.objectives_autosens_objective, R.string.objectives_autosens_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(7).msecs())
                .learned(Learned(R.string.objectives_autosens_learned))
        )
    }
}