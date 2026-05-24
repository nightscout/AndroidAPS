package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

class CommandTempBasalPercent(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val percent: Int,
    private val durationInMinutes: Int,
    private val enforceNew: Boolean,
    private val tbrType: PumpSync.TemporaryBasalType,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.TEMPBASAL

    override suspend fun execute(): PumpEnactResult {
        val r = if (percent == 100)
            activePlugin.activePump.cancelTempBasal(enforceNew)
        else
            activePlugin.activePump.setTempBasalPercent(percent, durationInMinutes, enforceNew, tbrType)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result percent: $percent durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
        return r
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.temp_basal_percent, percent, durationInMinutes)

    override fun log(): String = "TEMP BASAL $percent% $durationInMinutes min"
}
