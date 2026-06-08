package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class LoopSuspendAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        val duration = params.optInt("duration", 60)
        val normalizedDuration = duration.coerceIn(1, 180)
        
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.SUSPENDED_BY_USER)) {
            return commandNotPossible()
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.SUSPENDED_BY_USER,
            durationInMinutes = normalizedDuration,
            action = Action.SUSPEND,
            source = Sources.NfcCommands,
            profile = profile,
        )
        val messageId = if (result) CoreUiR.string.loopsuspended else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}
