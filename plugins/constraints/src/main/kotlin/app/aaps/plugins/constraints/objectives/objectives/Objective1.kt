package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.actions.Actions
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
import javax.inject.Inject

class Objective1 @Inject constructor(injector: HasAndroidInjector) : Objective(injector, "usage", R.string.objectives_usage_objective, R.string.objectives_usage_gate) {

    @Inject lateinit var activePlugin: ActivePlugin
    val actionsPlugin: PluginBase
        get() = activePlugin.getSpecificPluginsListByInterface(app.aaps.core.interfaces.actions.Actions::class.java)[0]

    init {
        tasks.add(object : Task(this, R.string.objectives_useprofileswitch) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectiveuseprofileswitch, false)
            }
        })
        tasks.add(object : Task(this, R.string.objectives_usedisconnectpump) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectiveusedisconnect, false)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usereconnectpump) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectiveusereconnect, false)
            }
        }.hint(Hint(R.string.disconnectpump_hint)))
        tasks.add(object : Task(this, R.string.objectives_usetemptarget) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectiveusetemptarget, false)
            }
        }.hint(Hint(R.string.usetemptarget_hint)))
        tasks.add(object : Task(this, R.string.objectives_useactions) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectiveuseactions, false) && actionsPlugin.isEnabled() && actionsPlugin.isFragmentVisible()
            }
        }.hint(Hint(R.string.useaction_hint)))
        tasks.add(object : Task(this, R.string.objectives_useloop) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectiveuseloop, false)
            }
        }.hint(Hint(R.string.useaction_hint)))
        tasks.add(
            object : Task(this, R.string.objectives_usescale) {
                override fun isCompleted(): Boolean {
                    return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectiveusescale, false)
                }
            }.hint(Hint(R.string.usescale_hint))
                .learned(Learned(R.string.objectives_usage_learned))
        )
    }
}