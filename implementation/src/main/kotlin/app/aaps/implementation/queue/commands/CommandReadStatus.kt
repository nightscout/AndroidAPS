package app.aaps.implementation.queue.commands

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

class CommandReadStatus(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val localAlertUtils: LocalAlertUtils,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    val reason: String,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.READSTATUS

    override suspend fun execute(): PumpEnactResult {
        activePlugin.activePump.getPumpStatus(reason)
        localAlertUtils.reportPumpStatusRead()
        aapsLogger.debug(LTag.PUMPQUEUE, "CommandReadStatus executed. Reason: $reason")
        val pump = activePlugin.activePump
        val result = pumpEnactResultProvider.get().success(false)
        val lastConnection = pump.lastDataTime.value
        if (lastConnection > System.currentTimeMillis() - T.mins(1).msecs()) result.success(true)
        return result
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.read_status, reason)

    override fun log(): String = "READSTATUS $reason"
}
