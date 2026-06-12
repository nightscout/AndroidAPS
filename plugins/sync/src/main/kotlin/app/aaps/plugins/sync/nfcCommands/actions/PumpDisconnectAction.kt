package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import org.json.JSONObject
import app.aaps.core.ui.R as CoreUiR

class PumpDisconnectAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val duration = params.optInt("duration", 30).coerceIn(1, 180)
        val profile = plugin.profileFunction.getProfile() ?: return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.noprofile))

        val result = plugin.loop.handleRunningModeChange(
            durationInMinutes = duration,
            profile = profile,
            newRM = RM.Mode.DISCONNECTED_PUMP,
            action = Action.DISCONNECT,
            source = source,
        )
        if (result) {
            uel.log(
                action = Action.DISCONNECT,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.RMMode(RM.Mode.DISCONNECTED_PUMP),
                    ValueWithUnit.Minute(duration)
                )
            )
        }
        val messageId = if (result) CoreUiR.string.pump_disconnected else R.string.nfccommands_remote_command_not_possible
        return NfcExecutionResult(result, plugin.rh.gs(messageId))
    }
}
