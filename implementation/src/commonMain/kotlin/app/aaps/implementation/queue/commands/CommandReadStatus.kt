package app.aaps.implementation.queue.commands

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings
import kotlin.time.Clock

class CommandReadStatus(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    private val localAlertUtils: LocalAlertUtils,
    override val pumpEnactResultProvider: () -> PumpEnactResult,
    val reason: String,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.READSTATUS

    override suspend fun execute(): PumpEnactResult {
        activePlugin.activePump.getPumpStatus(reason)
        localAlertUtils.reportPumpStatusRead()
        aapsLogger.debug(LTag.PUMPQUEUE, "CommandReadStatus executed. Reason: $reason")
        val pump = activePlugin.activePump
        val result = pumpEnactResultProvider().success(false)
        val lastConnection = pump.lastDataTime.value
        if (lastConnection > Clock.System.now().toEpochMilliseconds() - T.mins(1).msecs()) result.success(true)
        return result
    }

    override fun status(): String = rh.gs(CoreUiStrings.read_status, reason)

    override fun log(): String = "READSTATUS $reason"
}
