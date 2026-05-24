package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

class CommandBolus(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val bolusProgressData: BolusProgressData,
    private val detailedBolusInfo: DetailedBolusInfo,
    override val callback: Callback?,
    type: Command.CommandType,
) : Command {

    override var commandType: Command.CommandType = type

    override suspend fun execute(): PumpEnactResult {
        val r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        if (r.success) bolusProgressData.completeAndAutoClear()
        else bolusProgressData.clear()
        return r
    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) rh.gs(app.aaps.core.ui.R.string.bolus_u_min, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) rh.gs(app.aaps.core.ui.R.string.carbs_g, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun log(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(app.aaps.core.ui.R.string.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun cancel(commentResId: Int, success: Boolean) {
        super.cancel(commentResId, success)
        bolusProgressData.clear()
    }
}
