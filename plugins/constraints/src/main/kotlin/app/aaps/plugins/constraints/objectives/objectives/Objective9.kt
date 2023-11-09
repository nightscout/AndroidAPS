package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.utils.T
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector

class Objective9(injector: HasAndroidInjector) : Objective(injector, "smb", R.string.objectives_smb_objective, R.string.objectives_smb_gate) {

    init {
        tasks.add(
            MinimumDurationTask(this, T.days(28).msecs())
                .learned(Learned(R.string.objectives_smb_learned))
        )
    }
}