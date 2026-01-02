package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class Objective3 @Inject constructor(
    preferences: Preferences,
    rh: ResourceHelper,
    dateUtil: DateUtil,
) : Objective(preferences, rh, dateUtil, "openloop", R.string.objectives_openloop_objective, R.string.objectives_openloop_gate) {

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