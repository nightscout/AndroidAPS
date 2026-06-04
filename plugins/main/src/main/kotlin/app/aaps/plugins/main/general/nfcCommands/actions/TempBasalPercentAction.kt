package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class TempBasalPercentAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        var tempBasalPct = params.optInt("percent", 100)
        val durationStep = plugin.pumpBasalDurationStep()
        val rawDuration = params.optInt("duration", durationStep)
        
        if (rawDuration <= 0) return invalidFormat()
        
        val duration = plugin.roundUpToStep(rawDuration, durationStep)
        tempBasalPct = plugin.constraintChecker.applyBasalPercentConstraints(ConstraintObject(tempBasalPct, plugin.aapsLogger), profile).value()
        val result = plugin.commandQueue.tempBasalPercent(
            tempBasalPct,
            duration,
            true,
            profile,
            PumpSync.TemporaryBasalType.NORMAL,
        )
        return if (result.success) {
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_command_executed, "BASAL $tempBasalPct% ${duration}min"))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "tempBasalPercent failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
