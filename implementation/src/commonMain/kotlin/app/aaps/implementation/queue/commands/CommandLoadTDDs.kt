package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.UiStrings

class CommandLoadTDDs(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: () -> PumpEnactResult,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.LOAD_TDD

    override suspend fun execute(): PumpEnactResult =
        activePlugin.activePump.loadTDDs().also {
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${it.success} enacted: ${it.enacted}")
        }

    override fun status(): String = rh.gs(UiStrings.load_tdds)

    override fun log(): String = "LOAD TDDs"
}
