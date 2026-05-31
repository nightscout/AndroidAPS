package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.core.ui.R as CoreUiR
import org.json.JSONObject

class BasalCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        val result = plugin.commandQueue.cancelTempBasal(enforceNew = true)
        return if (result.success) {
            uel.log(
                action = Action.CANCEL_TEMP_BASAL,
                note = tagName,
                source = source,
            )
            NfcExecutionResult(true, plugin.rh.gs(CoreUiR.string.stoptemptarget))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "cancelTempBasal failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
