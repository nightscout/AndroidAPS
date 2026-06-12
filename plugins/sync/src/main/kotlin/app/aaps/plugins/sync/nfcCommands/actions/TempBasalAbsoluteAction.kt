package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class TempBasalAbsoluteAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))
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
        if (result.success) {
            uel.log(
                action = Action.TEMP_BASAL,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.UnitPerHour(tempBasal),
                    ValueWithUnit.Minute(duration)
                )
            )
            val rateString = plugin.decimalFormatter.to2Decimal(tempBasal)
            return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_command_executed, "BASAL $rateString U/h ${duration}min"))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "tempBasalAbsolute failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}
