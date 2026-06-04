package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class PumpConnectAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        if (divided.size != 2) return invalidFormat()
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.RESUME)) {
            return NfcExecutionResult(true, plugin.rh.gs(app.aaps.core.interfaces.R.string.connected))
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.RESUME,
            action = Action.RECONNECT,
            source = Sources.NfcCommands,
            profile = profile,
        )
        val messageId = if (result) R.string.nfccommands_reconnect else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}
