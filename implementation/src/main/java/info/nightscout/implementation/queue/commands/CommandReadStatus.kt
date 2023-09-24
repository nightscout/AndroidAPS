package info.nightscout.implementation.queue.commands

import app.aaps.interfaces.alerts.LocalAlertUtils
import app.aaps.interfaces.logging.LTag
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.pump.PumpEnactResult
import app.aaps.interfaces.queue.Callback
import app.aaps.interfaces.queue.Command
import app.aaps.interfaces.utils.T
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class CommandReadStatus(
    injector: HasAndroidInjector,
    val reason: String,
    callback: Callback?
) : Command(injector, CommandType.READSTATUS, callback) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils

    override fun execute() {
        activePlugin.activePump.getPumpStatus(reason)
        localAlertUtils.notifyPumpStatusRead()
        aapsLogger.debug(LTag.PUMPQUEUE, "CommandReadStatus executed. Reason: $reason")
        val pump = activePlugin.activePump
        val result = PumpEnactResult(injector).success(false)
        val lastConnection = pump.lastDataTime()
        if (lastConnection > System.currentTimeMillis() - T.mins(1).msecs()) result.success(true)
        callback?.result(result)?.run()
    }

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.read_status, reason)

    override fun log(): String = "READSTATUS $reason"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}