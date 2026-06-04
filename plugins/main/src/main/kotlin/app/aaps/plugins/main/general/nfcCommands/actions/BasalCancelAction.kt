package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class BasalCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        val result = plugin.commandQueue.cancelTempBasal(enforceNew = true)
        return if (result.success) {
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_tempbasal_canceled))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "cancelTempBasal failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
