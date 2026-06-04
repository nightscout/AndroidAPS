package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class TempBasalAbsoluteAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        var tempBasal = params.optDouble("rate", 0.0)
        val durationStep = plugin.pumpBasalDurationStep()
        val rawDuration = params.optInt("duration", durationStep)
        
        if (rawDuration <= 0) return invalidFormat()
        
        val duration = plugin.roundUpToStep(rawDuration, durationStep)
        tempBasal = plugin.constraintChecker.applyBasalConstraints(ConstraintObject(tempBasal, plugin.aapsLogger), profile).value()
        val result = plugin.commandQueue.tempBasalAbsolute(
            tempBasal,
            duration,
            true,
            profile,
            PumpSync.TemporaryBasalType.NORMAL,
        )
        return if (result.success) {
            val rateString = plugin.decimalFormatter.to2Decimal(tempBasal)
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_command_executed, "BASAL $rateString U/h ${duration}min"))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "tempBasalAbsolute failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
