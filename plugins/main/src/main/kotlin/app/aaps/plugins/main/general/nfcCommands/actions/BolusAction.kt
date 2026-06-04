package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import java.util.concurrent.TimeUnit

class BolusAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        if (divided.size !in 2..3) return invalidFormat()
        if (plugin.commandQueue.bolusInQueue()) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_another_bolus_in_queue))
        }
        if (plugin.dateUtil.now() - plugin.lastRemoteBolusTime < Constants.remoteBolusMinDistance) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_bolus_not_allowed))
        }
        if (plugin.loop.runningMode().pausesLoopExecution()) {
            return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.pumpsuspended))
        }
        var bolus = SafeParse.stringToDouble(divided[1])
        val isMeal = divided.size > 2 && divided[2].equals("MEAL", ignoreCase = true)
        bolus = plugin.constraintChecker.applyBolusConstraints(ConstraintObject(bolus, plugin.aapsLogger)).value()
        if (divided.size == 3 && !isMeal) return invalidFormat()
        if (bolus <= 0.0) return invalidFormat()
        val detailedBolusInfo = DetailedBolusInfo().apply { insulin = bolus }
        val result = plugin.commandQueue.bolus(detailedBolusInfo)
        if (!result.success) {
            plugin.aapsLogger.error(LTag.NFC, "bolus failed: ${result.comment}")
            return commandNotPossible()
        }
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
                        ValueWithUnit.Minute(
                            TimeUnit.MILLISECONDS.toMinutes(TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong())).toInt(),
                        ),
                    ),
                )
            }
        }
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_command_executed, "BOLUS ${divided.drop(1).joinToString(" ")}"))
    }
}
