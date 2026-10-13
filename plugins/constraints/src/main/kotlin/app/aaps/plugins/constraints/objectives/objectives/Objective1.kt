package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Objective1 @Inject constructor(
    preferences: Preferences,
    rh: ResourceHelper,
    dateUtil: DateUtil,
    private val activePlugin: ActivePlugin
) : Objective(preferences, rh, dateUtil, "usage", R.string.objectives_usage_objective, R.string.objectives_usage_gate) {

    val actionsPlugin: PluginBase
        get() = activePlugin.getSpecificPluginsListByInterface(app.aaps.core.interfaces.actions.Actions::class.java)[0]

    init {
        tasks.add(object : Task(this, R.string.objectives_useprofileswitch) {
            override fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesProfileSwitchUsed)
            }
        })
        tasks.add(object : Task(this, R.string.objectives_usedisconnectpump) {
            override fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesDisconnectUsed)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usereconnectpump) {
            override fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesReconnectUsed)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usetemptarget) {
            override fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesTempTargetUsed)
            }
        }.hint(Hint(R.string.usetemptarget_hint)))
        tasks.add(object : Task(this, R.string.objectives_useactions) {
            override fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesActionsUsed) && actionsPlugin.isEnabled() && actionsPlugin.isFragmentVisible()
            }
        }.hint(Hint(R.string.useaction_hint)))
        tasks.add(object : Task(this, R.string.objectives_useloop) {
            override fun isCompleted(): Boolean {
                return preferences.get(BooleanNonKey.ObjectivesLoopUsed)
            }
        }.hint(Hint(R.string.useaction_hint)))
        tasks.add(
            object : Task(this, R.string.objectives_usescale) {
                override fun isCompleted(): Boolean {
                    return preferences.get(BooleanNonKey.ObjectivesScaleUsed)
                }
            }.hint(Hint(R.string.usescale_hint))
                .learned(Learned(R.string.objectives_usage_learned))
        )
    }
}