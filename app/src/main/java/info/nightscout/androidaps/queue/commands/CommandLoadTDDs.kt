package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandLoadTDDs(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.LOAD_TDD, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val pump = activePlugin.activePump
        val r = pump.loadTDDs()
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
        callback?.result(r)?.run()
    }

    override fun status(): String = "LOAD TDDs"
}