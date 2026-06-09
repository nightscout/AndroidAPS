package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class CarbsAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        var grams = params.optInt("amount", 0)
        
        if (grams == 0) return invalidFormat()
        
        grams = plugin.constraintChecker.applyCarbsConstraints(ConstraintObject(grams, plugin.aapsLogger)).value()

        val detailedBolusInfo = DetailedBolusInfo().apply {
            carbs = grams.toDouble()
            timestamp = plugin.dateUtil.now()
        }
        val result = plugin.commandQueue.bolus(detailedBolusInfo)
        if (result.success) {
            uel.log(
                action = Action.CARBS,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.Gram(grams)
                )
            )
            return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_carbs_set, grams))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "carbs bolus failed: ${result.comment}")
            return commandNotPossible()
        }
    }
}
