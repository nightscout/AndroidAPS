package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Dana
import app.aaps.core.interfaces.pump.Diaconn
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

class CommandLoadHistory(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val type: Byte,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.LOAD_HISTORY

    override suspend fun execute(): PumpEnactResult {
        val pump = activePlugin.activePumpInternal
        val result = when (pump) {
            is Dana    -> pump.loadHistory(type)
            is Diaconn -> pump.loadHistory()
            else       -> pumpEnactResultProvider.get().success(true).enacted(false)
        }
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${result.success} enacted: ${result.enacted}")
        return result
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.load_history, type.toInt())

    override fun log(): String = "LOAD HISTORY $type"
}
