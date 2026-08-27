package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Dana
import app.aaps.core.interfaces.pump.Diaconn
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.UiStrings

class CommandLoadHistory(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: () -> PumpEnactResult,
    private val type: Byte,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.LOAD_HISTORY

    override suspend fun execute(): PumpEnactResult {
        val pump = activePlugin.activePumpInternal
        val result = when (pump) {
            is Dana    -> pump.loadHistory(type)
            is Diaconn -> pump.loadHistory()
            else       -> pumpEnactResultProvider().success(true).enacted(false)
        }
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${result.success} enacted: ${result.enacted}")
        return result
    }

    override fun status(): String = rh.gs(UiStrings.load_history, type.toInt())

    override fun log(): String = "LOAD HISTORY $type"
}
