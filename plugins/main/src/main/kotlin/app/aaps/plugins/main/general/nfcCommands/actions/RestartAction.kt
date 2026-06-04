package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Sources
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class RestartAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        plugin.configBuilder.exitApp("NFC", Sources.NfcCommands, true)
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_restarting))
    }
}
