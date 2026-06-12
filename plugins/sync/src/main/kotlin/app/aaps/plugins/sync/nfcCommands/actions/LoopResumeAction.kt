package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class LoopResumeAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))
        if (!plugin.loop.allowedNextModes().contains(RM.Mode.RESUME) && plugin.loop.runningMode() != RM.Mode.DISABLED_LOOP) {
            return commandNotPossible()
        }
        val result = plugin.loop.handleRunningModeChange(
            newRM = RM.Mode.RESUME,
            action = Action.RESUME,
            source = source,
            profile = profile,
        )
        if (result) {
            uel.log(
                action = Action.RESUME,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.RESUME)
                )
            )
        }
        val messageId = if (result) R.string.nfccommands_loop_resumed else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}
