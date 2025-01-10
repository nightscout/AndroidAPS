package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.IntNonKey
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective3 @Inject constructor(injector: HasAndroidInjector) : Objective(injector, "openloop", R.string.objectives_openloop_objective, R.string.objectives_openloop_gate) {

    @Inject lateinit var activePlugin: ActivePlugin

    init {
        tasks.add(MinimumDurationTask(this, T.days(7).msecs()))
        tasks.add(
            object : Task(this, R.string.objectives_manualenacts) {
                override fun isCompleted(): Boolean {
                    return preferences.get(IntNonKey.ObjectivesManualEnacts) >= MANUAL_ENACTS_NEEDED
                }

                override val progress: String
                    get() =
                        if (preferences.get(IntNonKey.ObjectivesManualEnacts) >= MANUAL_ENACTS_NEEDED)
                            rh.gs(R.string.completed_well_done)
                        else preferences.get(IntNonKey.ObjectivesManualEnacts).toString() + " / " + MANUAL_ENACTS_NEEDED
            }.learned(Learned(R.string.objectives_openloop_learned))
        )
    }

    companion object {

        private const val MANUAL_ENACTS_NEEDED = 20
    }
}