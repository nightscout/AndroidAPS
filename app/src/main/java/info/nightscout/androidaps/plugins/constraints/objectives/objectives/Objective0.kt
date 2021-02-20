package info.nightscout.androidaps.plugins.constraints.objectives.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import javax.inject.Inject

class Objective0(injector: HasAndroidInjector) : Objective(injector, "config", R.string.objectives_0_objective, R.string.objectives_0_gate) {

    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    init {
        tasks.add(object : Task(this, R.string.objectives_bgavailableinns) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_ObjectivesbgIsAvailableInNS, false)
            }
        })
        tasks.add(object : Task(this, R.string.nsclienthaswritepermission) {
            override fun isCompleted(): Boolean {
                return nsClientPlugin.hasWritePermission()
            }
        })
        tasks.add(object : Task(this, R.string.virtualpump_uploadstatus_title) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_virtualpump_uploadstatus, false)
            }

            override fun shouldBeIgnored(): Boolean {
                return !virtualPumpPlugin.isEnabled(PluginType.PUMP)
            }
        })
        tasks.add(object : Task(this, R.string.objectives_pumpstatusavailableinns) {
            override fun isCompleted(): Boolean {
                return sp.getBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, false)
            }
        })
        tasks.add(object : Task(this, R.string.hasbgdata) {
            override fun isCompleted(): Boolean {
                return iobCobCalculatorPlugin.lastBg() != null
            }
        })
        tasks.add(object : Task(this, R.string.loopenabled) {
            override fun isCompleted(): Boolean {
                return loopPlugin.isEnabled(PluginType.LOOP)
            }
        })
        tasks.add(object : Task(this, R.string.apsselected) {
            override fun isCompleted(): Boolean {
                val usedAPS = activePlugin.activeAPS
                return (usedAPS as PluginBase).isEnabled(PluginType.APS)
            }
        })
        tasks.add(object : Task(this, R.string.activate_profile) {
            override fun isCompleted(): Boolean {
                return treatmentsPlugin.getProfileSwitchFromHistory(DateUtil.now()) != null
            }
        })
    }
}