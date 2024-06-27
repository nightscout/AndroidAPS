package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.sync.Tidepool
import app.aaps.database.ValueWrapper
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class Objective0(injector: HasAndroidInjector) : Objective(injector, "config", R.string.objectives_0_objective, R.string.objectives_0_gate) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var virtualPumpPlugin: VirtualPump
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var loop: Loop
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    val tidepoolPlugin get() = activePlugin.getSpecificPluginsListByInterface(Tidepool::class.java).firstOrNull() as Tidepool?

    init {
        tasks.add(object : Task(this, R.string.objectives_bgavailableinns) {
            override fun isCompleted(): Boolean {
                return true
            }
        })
        tasks.add(object : Task(this, R.string.synchaswritepermission) {
            override fun isCompleted(): Boolean {
                return true
            }
        })
        tasks.add(object : Task(this, app.aaps.core.ui.R.string.virtualpump_uploadstatus_title) {
            override fun isCompleted(): Boolean {
                return true
            }

            override fun shouldBeIgnored(): Boolean {
                return !virtualPumpPlugin.isEnabled()
            }
        })
        tasks.add(
            object : Task(this, R.string.objectives_pumpstatusavailableinns) {
                override fun isCompleted(): Boolean {
                    return true
                }
            }.learned(Learned(R.string.objectives_0_learned))
        )
        tasks.add(object : Task(this, R.string.hasbgdata) {
            override fun isCompleted(): Boolean {
                return true
            }
        })
        tasks.add(object : Task(this, R.string.loopenabled) {
            override fun isCompleted(): Boolean {
                return true
            }
        })
        tasks.add(object : Task(this, R.string.apsselected) {
            override fun isCompleted(): Boolean {
                val usedAPS = activePlugin.activeAPS
                return true
            }
        })
        tasks.add(object : Task(this, app.aaps.core.ui.R.string.activate_profile) {
            override fun isCompleted(): Boolean {
                return true
            }
        })
    }
}