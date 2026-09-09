package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

// Contributed rather than listed in a graph. ObjectivesPlugin takes List<Objective> and is itself
// contributed to AppScope, so the objectives have to be reachable from the same graph.
@ContributesIntoMap(AppScope::class, binding = binding<Objective>())
@IntKey(1)
@SingleIn(AppScope::class)
class Objective1 @Inject constructor(
    preferences: Preferences,
    rh: TextResolver,
    durationText: DurationText,
    dateUtil: DateUtil
) : Objective(preferences, rh, dateUtil, durationText, "usage", ConstraintsStrings.objectives_usage_objective, ConstraintsStrings.objectives_usage_gate) {

    init {
        tasks.add(object : Task(this, ConstraintsStrings.objectives_useprofileswitch) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesProfileSwitchUsed)
            }
        })
        tasks.add(object : Task(this, ConstraintsStrings.objectives_usedisconnectpump) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesDisconnectUsed)
            }
        }.hint(Hint(ConstraintsStrings.disconnectpump_hint)))
        tasks.add(object : Task(this, ConstraintsStrings.objectives_usereconnectpump) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesReconnectUsed)
            }
        }.hint(Hint(ConstraintsStrings.disconnectpump_hint)))
        tasks.add(object : Task(this, ConstraintsStrings.objectives_usetemptarget) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesTempTargetUsed)
            }
        }.hint(Hint(ConstraintsStrings.usetemptarget_hint)))
        tasks.add(object : Task(this, ConstraintsStrings.objectives_useloop) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesLoopUsed)
            }
        }.hint(Hint(ConstraintsStrings.useaction_hint)))
        tasks.add(
            object : Task(this, ConstraintsStrings.objectives_usescale) {
                override suspend fun isCompleted(): Boolean {
                    return preferences.get(BooleanNonKey.ObjectivesScaleUsed)
                }
            }.hint(Hint(ConstraintsStrings.usescale_hint))
                .learned(Learned(ConstraintsStrings.objectives_usage_learned))
        )
    }
}