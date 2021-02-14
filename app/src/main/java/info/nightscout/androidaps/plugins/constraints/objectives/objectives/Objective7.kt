package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.T

class Objective7(injector: HasAndroidInjector) : Objective(injector, "autosens", R.string.objectives_autosens_objective, R.string.objectives_autosens_gate) {

    init {
        tasks.add(MinimumDurationTask(this, T.days(7).msecs()))
    }
}