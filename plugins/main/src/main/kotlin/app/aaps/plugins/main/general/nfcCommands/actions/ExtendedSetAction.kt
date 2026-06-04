package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class ExtendedSetAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        var amount = params.optDouble("amount", 0.0)
        val duration = params.optInt("duration", 0)
        
        if (amount <= 0.0 || duration <= 0) return invalidFormat()
        
        amount = plugin.constraintChecker.applyExtendedBolusConstraints(ConstraintObject(amount, plugin.aapsLogger)).value()
        
        val result = plugin.commandQueue.extendedBolus(amount, duration)
        return if (result.success) {
            val amountString = plugin.decimalFormatter.to2Decimal(amount)
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_extended_set, amountString, duration))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "extendedBolus failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
