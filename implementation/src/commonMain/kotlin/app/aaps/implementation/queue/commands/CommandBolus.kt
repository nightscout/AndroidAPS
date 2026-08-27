package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

class CommandBolus(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    override val pumpEnactResultProvider: () -> PumpEnactResult,
    private val bolusProgressData: BolusProgressData,
    private val detailedBolusInfo: DetailedBolusInfo,
    override val callback: Callback?,
    type: Command.CommandType,
    private val bolusGeneration: Long,
) : Command {

    override var commandType: Command.CommandType = type

    override suspend fun execute(): PumpEnactResult {
        val r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        // Generation-scoped both ways: never stamp completion onto / wipe a NEWER bolus enqueued behind this one
        // (an SMB + manual bolus get adjacent generations at enqueue; see BolusProgressData.clear / completeAndAutoClear).
        if (r.success) bolusProgressData.completeAndAutoClear(bolusGeneration)
        else bolusProgressData.clear(bolusGeneration)
        return r
    }

    override fun status(): String {
        return (if (detailedBolusInfo.insulin > 0) rh.gs(CoreUiStrings.bolus_u_min, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) rh.gs(CoreUiStrings.carbs_g, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun log(): String {
        return (if (detailedBolusInfo.insulin > 0) "BOLUS " + rh.gs(InterfacesStrings.format_insulin_units, detailedBolusInfo.insulin) else "") +
            if (detailedBolusInfo.carbs > 0) "CARBS " + rh.gs(InterfacesStrings.format_carbs, detailedBolusInfo.carbs.toInt()) else ""
    }

    override fun cancel(comment: TextRef, success: Boolean) {
        super.cancel(comment, success)
        bolusProgressData.clear(bolusGeneration)
    }
}
