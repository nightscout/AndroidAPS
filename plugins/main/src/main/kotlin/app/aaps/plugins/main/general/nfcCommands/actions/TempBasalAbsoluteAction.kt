package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class TempBasalAbsoluteAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        if (divided.size !in 2..3) return invalidFormat()
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        var tempBasal = SafeParse.stringToDouble(divided[1])
        val durationStep = plugin.pumpBasalDurationStep()
        val rawDuration = divided.getOrNull(2)?.let { SafeParse.stringToInt(it) } ?: durationStep
        if (tempBasal == 0.0 && divided[1] != "0") return invalidFormat()
        if (rawDuration <= 0) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_wrong_tbr_duration, durationStep))
        }
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
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_command_executed, "BASAL ${divided.drop(1).joinToString(" ")}"))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "tempBasalAbsolute failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
