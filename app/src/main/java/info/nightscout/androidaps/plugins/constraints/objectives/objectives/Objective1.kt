package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.general.actions.ActionsPlugin
import javax.inject.Inject

class Objective1 @Inject constructor(injector: HasAndroidInjector) : Objective(injector, "usage", R.string.objectives_usage_objective, R.string.objectives_usage_gate) {

    @Inject lateinit var actionsPlugin: ActionsPlugin

    init {
        tasks.add(object : Task(this, R.string.objectives_useprofileswitch) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectiveuseprofileswitch, false)
            }
        })
        tasks.add(object : Task(this, R.string.objectives_usedisconnectpump) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectiveusedisconnect, false)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usereconnectpump) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectiveusereconnect, false)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usetemptarget) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectiveusetemptarget, false)
            }
        }.hint(Hint(R.string.usetemptarget_hint)))
        tasks.add(object : Task(this, R.string.objectives_useactions) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectiveuseactions, false) && actionsPlugin.isEnabled(PluginType.GENERAL) && actionsPlugin.isFragmentVisible()
            }
        }.hint(Hint(R.string.useaction_hint)))
        tasks.add(object : Task(this, R.string.objectives_useloop) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectiveuseloop, false)
            }
        }.hint(Hint(R.string.useaction_hint)))
        tasks.add(object : Task(this, R.string.objectives_usescale) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_objectiveusescale, false)
            }
        }.hint(Hint(R.string.usescale_hint)))
    }
}