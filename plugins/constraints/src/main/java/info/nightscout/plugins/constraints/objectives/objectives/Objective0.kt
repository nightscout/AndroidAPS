package info.nightscout.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.database.ValueWrapper
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.pump.VirtualPump
import info.nightscout.plugins.constraints.R
import javax.inject.Inject

class Objective0(injector: HasAndroidInjector) : Objective(injector, "config", R.string.objectives_0_objective, R.string.objectives_0_gate) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var virtualPumpPlugin: VirtualPump
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var loop: Loop
    @Inject lateinit var iobCobCalculator: IobCobCalculator

    init {
        tasks.add(object : Task(this, R.string.objectives_bgavailableinns) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectives_bg_is_available_in_ns, false)
            }
        })
        tasks.add(object : Task(this, R.string.synchaswritepermission) {
            override fun isCompleted(): Boolean {
                return activePlugin.firstActiveSync?.hasWritePermission == true
            }
        })
        tasks.add(object : Task(this, info.nightscout.core.ui.R.string.virtualpump_uploadstatus_title) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(info.nightscout.core.utils.R.string.key_virtual_pump_upload_status, false)
            }

            override fun shouldBeIgnored(): Boolean {
                return !virtualPumpPlugin.isEnabled()
            }
        })
        tasks.add(
            object : Task(this, R.string.objectives_pumpstatusavailableinns) {
                override fun isCompleted(): Boolean {
                    return sp.getBoolean(info.nightscout.core.utils.R.string.key_objectives_pump_status_is_available_in_ns, false)
                }
            }.learned(Learned(R.string.objectives_0_learned))
        )
        tasks.add(object : Task(this, R.string.hasbgdata) {
            override fun isCompleted(): Boolean {
                return iobCobCalculator.ads.lastBg() != null
            }
        })
        tasks.add(object : Task(this, R.string.loopenabled) {
            override fun isCompleted(): Boolean {
                return (loop as PluginBase).isEnabled()
            }
        })
        tasks.add(object : Task(this, R.string.apsselected) {
            override fun isCompleted(): Boolean {
                val usedAPS = activePlugin.activeAPS
                return (usedAPS as PluginBase).isEnabled()
            }
        })
        tasks.add(object : Task(this, info.nightscout.core.ui.R.string.activate_profile) {
            override fun isCompleted(): Boolean {
                return persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing
            }
        })
    }
}