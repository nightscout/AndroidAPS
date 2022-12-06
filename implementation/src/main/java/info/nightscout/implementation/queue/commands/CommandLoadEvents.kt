package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.Dana
import info.nightscout.interfaces.pump.Diaconn
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class CommandLoadEvents(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.LOAD_EVENTS, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is Dana) {
            val danaPump = pump as Dana
            val r = danaPump.loadEvents()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }

        if (pump is Diaconn) {
            val diaconnPump = pump as Diaconn
            val r = diaconnPump.loadHistory()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.load_events)

    override fun log(): String = "LOAD EVENTS"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}