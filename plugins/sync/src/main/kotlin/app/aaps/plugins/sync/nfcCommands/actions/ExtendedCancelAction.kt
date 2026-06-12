package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import org.json.JSONObject

class ExtendedCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val result = plugin.commandQueue.cancelExtended()
        return if (result.success) {
            uel.log(
                action = Action.CANCEL_EXTENDED_BOLUS,
                source = source,
                note = tagName,
            )
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_extended_canceled))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "cancelExtended failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
