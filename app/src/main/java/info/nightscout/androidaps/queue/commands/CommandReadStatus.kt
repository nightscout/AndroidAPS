package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.LocalAlertUtils
import info.nightscout.androidaps.utils.T
import javax.inject.Inject

class CommandReadStatus(
    injector: HasAndroidInjector,
    val reason: String,
    callback: Callback?
) : Command(injector, CommandType.READSTATUS, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider
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

    override fun status(): String = "READSTATUS $reason"
}