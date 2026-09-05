package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
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
@IntKey(5)
@SingleIn(AppScope::class)
class Objective5 @Inject constructor(
    preferences: Preferences,
    rh: TextResolver,
    durationText: DurationText,
    dateUtil: DateUtil,
) : Objective(preferences, rh, dateUtil, durationText, "maxiobzero", ConstraintsStrings.objectives_maxiobzero_objective, ConstraintsStrings.objectives_maxiobzero_gate) {

    init {
        tasks.add(MinimumDurationTask(this, T.days(5).msecs()).learned(Learned(ConstraintsStrings.objectives_maxiobzero_learned)))
    }
}