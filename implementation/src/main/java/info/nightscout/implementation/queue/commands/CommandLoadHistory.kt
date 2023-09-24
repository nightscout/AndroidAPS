package info.nightscout.implementation.queue.commands

import app.aaps.interfaces.logging.LTag
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.pump.Dana
import app.aaps.interfaces.pump.Diaconn
import app.aaps.interfaces.pump.PumpEnactResult
import app.aaps.interfaces.queue.Callback
import app.aaps.interfaces.queue.Command
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class CommandLoadHistory(
    injector: HasAndroidInjector,
    private val type: Byte,
    callback: Callback?
) : Command(injector, CommandType.LOAD_HISTORY, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is Dana) {
            val danaPump = pump as Dana
            val r = danaPump.loadHistory(type)
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
            callback?.result(r)?.run()
        }

        if (pump is Diaconn) {
            val diaconnG8Pump = pump as Diaconn
            val r = diaconnG8Pump.loadHistory()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: " + r.success + " enacted: " + r.enacted)
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.load_history, type.toInt())

    override fun log(): String = "LOAD HISTORY $type"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}