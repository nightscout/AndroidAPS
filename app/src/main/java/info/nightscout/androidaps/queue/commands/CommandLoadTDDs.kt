package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandLoadTDDs(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.LOAD_TDD, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        val r = pump.loadTDDs()
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
        callback?.result(r)?.run()
    }

    override fun status(): String = rh.gs(R.string.load_tdds)

    override fun log(): String = "LOAD TDDs"
}