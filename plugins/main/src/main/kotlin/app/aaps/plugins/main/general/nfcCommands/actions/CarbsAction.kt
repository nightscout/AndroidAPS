package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class CarbsAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        if (divided.size != 2) return invalidFormat()
        var grams = SafeParse.stringToInt(divided[1])
        grams = plugin.constraintChecker.applyCarbsConstraints(ConstraintObject(grams, plugin.aapsLogger)).value()
        if (grams == 0) return invalidFormat()
        val detailedBolusInfo = DetailedBolusInfo().apply {
            carbs = grams.toDouble()
            timestamp = plugin.dateUtil.now()
        }
        val result = plugin.commandQueue.bolus(detailedBolusInfo)
        return if (result.success) {
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_carbs_set, grams))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "carbs bolus failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
