package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.T

class Objective9(injector: HasAndroidInjector) : Objective(injector, "smb", R.string.objectives_smb_objective, R.string.objectives_smb_gate) {

    init {
        tasks.add(MinimumDurationTask(this, T.days(28).msecs()))
    }
}