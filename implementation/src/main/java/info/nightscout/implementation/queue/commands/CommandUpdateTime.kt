package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.Dana
import info.nightscout.interfaces.pump.Diaconn
import info.nightscout.interfaces.pump.Medtrum
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class CommandUpdateTime(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.UPDATE_TIME, callback) {

    @Inject lateinit var activePlugin: ActivePlugin

    override fun execute() {
        val pump = activePlugin.activePump

        if (pump is Medtrum) {
            val medtrumPump = pump as Medtrum
            val r = medtrumPump.updateTime()
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
            callback?.result(r)?.run()
        }
    }

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.update_time)

    override fun log(): String = "UPDATE TIME"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}
