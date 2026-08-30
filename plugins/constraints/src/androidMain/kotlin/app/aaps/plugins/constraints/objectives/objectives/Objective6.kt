package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.resources.ResourceHelper
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
@IntKey(6)
@SingleIn(AppScope::class)
class Objective6 @Inject constructor(
    preferences: Preferences,
    rh: ResourceHelper,
    dateUtil: DateUtil,
    private val constraintChecker: ConstraintsChecker,
    private val loop: Loop
) : Objective(preferences, rh, dateUtil, "maxiob", ConstraintsStrings.objectives_maxiob_objective, ConstraintsStrings.objectives_maxiob_gate) {

    init {
        tasks.add(MinimumDurationTask(this, T.days(1).msecs()))
        tasks.add(
            object : Task(this, ConstraintsStrings.closedmodeenabled) {
                override suspend fun isCompleted(): Boolean = loop.runningMode() == RM.Mode.CLOSED_LOOP
            })
        tasks.add(
            object : Task(this, ConstraintsStrings.maxiobset) {

                override suspend fun isCompleted(): Boolean {
                    val maxIOB = constraintChecker.getMaxIOBAllowed().value()
                    return maxIOB > 0
                }
            }.learned(Learned(ConstraintsStrings.objectives_maxiob_learned))
        )
    }
}
