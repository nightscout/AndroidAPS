package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import org.json.JSONObject

class ExtendedSetAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        var amount = params.optDouble("amount", 0.0)
        val duration = params.optInt("duration", 0)
        
        if (amount <= 0.0 || duration <= 0) return invalidFormat()
        
        amount = plugin.constraintChecker.applyExtendedBolusConstraints(ConstraintObject(amount, plugin.aapsLogger)).value()
        
        val result = plugin.commandQueue.extendedBolus(amount, duration)
        if (result.success) {
            uel.log(
                action = Action.EXTENDED_BOLUS,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.Insulin(amount),
                    ValueWithUnit.Minute(duration)
                )
            )
            val amountString = plugin.decimalFormatter.to2Decimal(amount)
            return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_extended_set, amountString, duration))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "extendedBolus failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}
