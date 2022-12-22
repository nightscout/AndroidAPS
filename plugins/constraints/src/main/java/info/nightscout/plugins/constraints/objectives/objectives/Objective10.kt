package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
import info.nightscout.shared.utils.T

class Objective10(injector: HasAndroidInjector) : Objective(injector, "auto", R.string.objectives_auto_objective, R.string.objectives_auto_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(28).msecs())
                .learned(Learned(R.string.objectives_autosens_learned))
        )
    }
}