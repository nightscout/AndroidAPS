package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CustomCommand
import javax.inject.Provider

class CommandCustomCommand(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    val customCommand: CustomCommand,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.CUSTOM_COMMAND

    override suspend fun execute(): PumpEnactResult {
        val r = activePlugin.activePump.executeCustomCommand(customCommand)
            ?: pumpEnactResultProvider.get().success(true).enacted(false)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        return r
    }

    override fun status(): String = customCommand.statusDescription

    override fun log(): String = customCommand.statusDescription
}
