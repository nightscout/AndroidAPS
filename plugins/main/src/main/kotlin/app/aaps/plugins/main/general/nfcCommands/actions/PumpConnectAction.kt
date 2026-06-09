package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class PumpConnectAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(app.aaps.core.ui.R.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.RESUME)) {
            return NfcExecutionResult(true, plugin.rh.gs(app.aaps.core.interfaces.R.string.connected))
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.RESUME,
            action = Action.RECONNECT,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.RECONNECT,
                source = source,
                note = tagName
            )
        }
        val messageId = if (result) R.string.nfccommands_reconnect else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}
