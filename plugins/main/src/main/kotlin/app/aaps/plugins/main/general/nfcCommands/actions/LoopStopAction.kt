package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class LoopStopAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.DISABLED_LOOP)) {
            return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.loopisdisabled))
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.DISABLED_LOOP,
            durationInMinutes = Int.MAX_VALUE,
            action = Action.LOOP_DISABLED,
            source = Sources.NfcCommands,
            profile = profile,
        )
        val messageId = if (result) R.string.nfccommands_loop_has_been_disabled else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}
