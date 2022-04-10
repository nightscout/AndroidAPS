package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R

@Suppress("SpellCheckingInspection")
class Objective4(injector: HasAndroidInjector) : Objective(injector, "maxbasal", R.string.objectives_maxbasal_objective, R.string.objectives_maxbasal_gate)