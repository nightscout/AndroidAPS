package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResultObject
import info.nightscout.implementation.R
import info.nightscout.interfaces.LocalAlertUtils
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.T
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
        val result = PumpEnactResultObject(injector).success(false)
        val lastConnection = pump.lastDataTime()
        if (lastConnection > System.currentTimeMillis() - T.mins(1).msecs()) result.success(true)
        callback?.result(result)?.run()
    }

    override fun status(): String = rh.gs(R.string.read_status, reason)

    override fun log(): String = "READSTATUS $reason"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResultObject(injector).success(false).comment(info.nightscout.core.main.R.string.connectiontimedout))?.run()
    }
}