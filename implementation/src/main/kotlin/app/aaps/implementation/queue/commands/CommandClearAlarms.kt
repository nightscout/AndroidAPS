package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Medtrum
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class CommandClearAlarms(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.CLEAR_ALARMS, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump

        if (pump is Medtrum) {
            val medtrumPump = pump as Medtrum
            val r = medtrumPump.clearAlarms()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.clear_alarms)

    override fun log(): String = "CLEAR ALARMS"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}
