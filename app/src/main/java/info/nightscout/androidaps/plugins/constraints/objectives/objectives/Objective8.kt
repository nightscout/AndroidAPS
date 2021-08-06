package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.T

class Objective8(injector: HasAndroidInjector) : Objective(injector, "ama", R.string.objectives_ama_objective, 0) {

    init {
        tasks.add(MinimumDurationTask(this, T.days(28).msecs()))
    }
}