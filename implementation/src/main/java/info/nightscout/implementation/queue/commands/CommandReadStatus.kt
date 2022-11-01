package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.LocalAlertUtils
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.T
import info.nightscout.implementation.R
import info.nightscout.shared.logging.LTag
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

    override fun status(): String = rh.gs(R.string.read_status, reason)

    override fun log(): String = "READSTATUS $reason"
}