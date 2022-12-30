package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
import info.nightscout.shared.utils.T

class Objective9(injector: HasAndroidInjector) : Objective(injector, "smb", R.string.objectives_smb_objective, R.string.objectives_smb_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(28).msecs())
                .learned(Learned(R.string.objectives_smb_learned))
        )
    }
}