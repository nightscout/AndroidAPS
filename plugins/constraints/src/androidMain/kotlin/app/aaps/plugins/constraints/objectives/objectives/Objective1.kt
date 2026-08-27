package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
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
    rh: ResourceHelper,
    dateUtil: DateUtil
) : Objective(preferences, rh, dateUtil, "usage", R.string.objectives_usage_objective, R.string.objectives_usage_gate) {

    init {
        tasks.add(object : Task(this, R.string.objectives_useprofileswitch) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesProfileSwitchUsed)
            }
        })
        tasks.add(object : Task(this, R.string.objectives_usedisconnectpump) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesDisconnectUsed)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usereconnectpump) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesReconnectUsed)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usetemptarget) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesTempTargetUsed)
            }
        }.hint(Hint(R.string.usetemptarget_hint)))
        tasks.add(object : Task(this, R.string.objectives_useloop) {
            override suspend fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesLoopUsed)
            }
        }.hint(Hint(R.string.useaction_hint)))
        tasks.add(
            object : Task(this, R.string.objectives_usescale) {
                override suspend fun isCompleted(): Boolean {
                    return preferences.get(BooleanNonKey.ObjectivesScaleUsed)
                }
            }.hint(Hint(R.string.usescale_hint))
                .learned(Learned(R.string.objectives_usage_learned))
        )
    }
}