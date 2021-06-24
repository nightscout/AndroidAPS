package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Dana
import info.nightscout.androidaps.interfaces.Diaconn
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
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

    override fun status(): String = "LOAD EVENTS"
}