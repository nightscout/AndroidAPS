package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

class CommandExtendedBolus(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val insulin: Double,
    private val durationInMinutes: Int,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.EXTENDEDBOLUS

    override suspend fun execute(): PumpEnactResult =
        activePlugin.activePump.setExtendedBolus(insulin, durationInMinutes).also {
            aapsLogger.debug(LTag.PUMPQUEUE, "Result rate: $insulin durationInMinutes: $durationInMinutes success: ${it.success} enacted: ${it.enacted}")
        }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.extended_bolus_u_min, insulin, durationInMinutes)

    override fun log(): String = "EXTENDEDBOLUS $insulin U $durationInMinutes min"
}
