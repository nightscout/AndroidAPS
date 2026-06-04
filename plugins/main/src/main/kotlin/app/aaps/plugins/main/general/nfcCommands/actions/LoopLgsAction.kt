package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class LoopLgsAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.CLOSED_LOOP_LGS)) {
            return commandNotPossible()
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.CLOSED_LOOP_LGS,
            action = Action.LGS_LOOP_MODE,
            source = Sources.NfcCommands,
            profile = profile,
        )
        val message = if (result) {
            plugin.rh.gs(R.string.nfccommands_current_loop_mode, plugin.rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend))
        } else {
            plugin.rh.gs(R.string.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}
