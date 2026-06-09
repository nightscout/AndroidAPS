package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BolusAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        if (plugin.commandQueue.bolusInQueue()) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_another_bolus_in_queue))
        }
        if (plugin.dateUtil.now() - plugin.lastRemoteBolusTime < Constants.remoteBolusMinDistance) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_bolus_not_allowed))
        }
        if (plugin.loop.runningMode().pausesLoopExecution()) {
            return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.pumpsuspended))
        }
        
        var bolus = params.optDouble("amount", 0.0)
        val isMeal = params.optBoolean("isMeal", false)
        
        if (bolus <= 0.0) return invalidFormat()
        
        bolus = plugin.constraintChecker.applyBolusConstraints(ConstraintObject(bolus, plugin.aapsLogger)).value()
        
        val detailedBolusInfo = DetailedBolusInfo().apply { insulin = bolus }
        val result = plugin.commandQueue.bolus(detailedBolusInfo)
        if (!result.success) {
            plugin.aapsLogger.error(LTag.NFC, "bolus failed: ${result.comment}")
            return commandNotPossible()
        }
        
        uel.log(
            action = if (isMeal) Action.TREATMENT else Action.BOLUS,
            source = source,
            note = tagName,
            listValues = listOf(
                ValueWithUnit.Insulin(bolus)
            )
        )

        plugin.setLastRemoteBolusTime(plugin.dateUtil.now())
        
        if (isMeal) {
            plugin.profileFunction.getProfile()?.let {
                val eatingSoonTTDuration = plugin.preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
                val eatingSoonTT = plugin.profileUtil.fromMgdlToUnits(plugin.preferences.ttTargetMgdl(TT.Reason.EATING_SOON), plugin.profileUtil.units)
                plugin.persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                    temporaryTarget = TT(
                        timestamp = plugin.dateUtil.now(),
                        duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                        reason = TT.Reason.EATING_SOON,
                        lowTarget = plugin.profileUtil.convertToMgdl(eatingSoonTT, plugin.profileUtil.units),
                        highTarget = plugin.profileUtil.convertToMgdl(eatingSoonTT, plugin.profileUtil.units),
                    ),
                    action = Action.TT,
                    source = Sources.NfcCommands,
                    note = null,
                    listValues = listOf(
                        ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                        ValueWithUnit.Mgdl(plugin.profileUtil.convertToMgdl(eatingSoonTT, plugin.profileUtil.units)),
                        ValueWithUnit.Minute(eatingSoonTTDuration),
                    ),
                )
            }
        }
        
        val amountString = plugin.decimalFormatter.to2Decimal(bolus)
        val logSuffix = if (isMeal) " (Meal)" else ""
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_command_executed, "BOLUS $amountString U$logSuffix"))
    }
}
