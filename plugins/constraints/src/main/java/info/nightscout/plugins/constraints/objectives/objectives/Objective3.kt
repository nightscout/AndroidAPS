package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.plugins.constraints.R
import info.nightscout.shared.utils.T
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class Objective3 @Inject constructor(injector: HasAndroidInjector) : Objective(injector, "openloop", R.string.objectives_openloop_objective, R.string.objectives_openloop_gate) {

    @Inject lateinit var activePlugin: ActivePlugin

    init {
        tasks.add(MinimumDurationTask(this, T.days(7).msecs()))
        tasks.add(
            object : Task(this, R.string.objectives_manualenacts) {
                override fun isCompleted(): Boolean {
                    return sp.getInt(info.nightscout.core.utils.R.string.key_ObjectivesmanualEnacts, 0) >= MANUAL_ENACTS_NEEDED
                }

                override val progress: String
                    get() =
                        if (sp.getInt(info.nightscout.core.utils.R.string.key_ObjectivesmanualEnacts, 0) >= MANUAL_ENACTS_NEEDED)
                            rh.gs(R.string.completed_well_done)
                        else sp.getInt(info.nightscout.core.utils.R.string.key_ObjectivesmanualEnacts, 0).toString() + " / " + MANUAL_ENACTS_NEEDED
            }.learned(Learned(R.string.objectives_openloop_learned))
        )
    }

    companion object {

        private const val MANUAL_ENACTS_NEEDED = 20
    }
}