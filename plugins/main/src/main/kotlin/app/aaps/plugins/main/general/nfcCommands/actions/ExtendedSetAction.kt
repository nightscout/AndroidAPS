package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class ExtendedSetAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        if (divided.size != 3) return invalidFormat()
        var extended = SafeParse.stringToDouble(divided[1])
        val duration = SafeParse.stringToInt(divided[2])
        extended = plugin.constraintChecker.applyExtendedBolusConstraints(ConstraintObject(extended, plugin.aapsLogger)).value()
        if (extended <= 0.0 || duration <= 0) return invalidFormat()
        val result = plugin.commandQueue.extendedBolus(extended, duration)
        return if (result.success) {
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_extended_set, extended, duration))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "extendedBolus failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
