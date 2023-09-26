package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.utils.T
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R

class Objective7(injector: HasAndroidInjector) : Objective(injector, "autosens", R.string.objectives_autosens_objective, R.string.objectives_autosens_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(7).msecs())
                .learned(Learned(R.string.objectives_autosens_learned))
        )
    }
}