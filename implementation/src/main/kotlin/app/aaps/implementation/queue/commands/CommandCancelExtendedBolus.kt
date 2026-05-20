package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

class CommandCancelExtendedBolus(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.EXTENDEDBOLUS

    override suspend fun execute(): PumpEnactResult =
        activePlugin.activePump.cancelExtendedBolus().also {
            aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${it.success} enacted: ${it.enacted}")
        }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.uel_cancel_extended_bolus)

    override fun log(): String = "CANCEL EXTENDEDBOLUS"
}
