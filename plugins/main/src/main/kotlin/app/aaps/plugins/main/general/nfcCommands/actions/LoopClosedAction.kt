package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class LoopClosedAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.CLOSED_LOOP)) {
            return commandNotPossible()
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.CLOSED_LOOP,
            action = Action.CLOSED_LOOP_MODE,
            source = Sources.NfcCommands,
            profile = profile,
        )
        val message = if (result) {
            plugin.rh.gs(R.string.nfccommands_current_loop_mode, plugin.rh.gs(app.aaps.core.ui.R.string.closedloop))
        } else {
            plugin.rh.gs(R.string.nfccommands_remote_command_not_possible)
        }
        return NfcExecutionResult(result, message)
    }
}
