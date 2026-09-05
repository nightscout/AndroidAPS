package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Suppress("SpellCheckingInspection")
// Contributed rather than listed in a graph. ObjectivesPlugin takes List<Objective> and is itself
// contributed to AppScope, so the objectives have to be reachable from the same graph.
@ContributesIntoMap(AppScope::class, binding = binding<Objective>())
@IntKey(3)
@SingleIn(AppScope::class)
class Objective3 @Inject constructor(
    preferences: Preferences,
    rh: TextResolver,
    durationText: DurationText,
    dateUtil: DateUtil,
) : Objective(preferences, rh, dateUtil, durationText, "openloop", ConstraintsStrings.objectives_openloop_objective, ConstraintsStrings.objectives_openloop_gate) {

    init {
        tasks.add(MinimumDurationTask(this, T.days(7).msecs()))
        tasks.add(
            object : Task(this, ConstraintsStrings.objectives_manualenacts) {
                override suspend fun isCompleted(): Boolean {
                    return preferences.get(IntNonKey.ObjectivesManualEnacts) >= MANUAL_ENACTS_NEEDED
                }

                override suspend fun progress(): String =
                    if (preferences.get(IntNonKey.ObjectivesManualEnacts) >= MANUAL_ENACTS_NEEDED)
                        rh.gs(ConstraintsStrings.completed_well_done)
                    else preferences.get(IntNonKey.ObjectivesManualEnacts).toString() + " / " + MANUAL_ENACTS_NEEDED
            }.learned(Learned(ConstraintsStrings.objectives_openloop_learned))
        )
    }

    companion object {

        private const val MANUAL_ENACTS_NEEDED = 20
    }
}