package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import org.json.JSONObject

class AapsClientRestartAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        plugin.activePlugin.getSpecificPluginsListByInterface(NsClient::class.java).forEach {
            (it as? NsClient)?.resend("NFC")
        }
        uel.log(
            action = Action.START_AAPS,
            note = tagName,
            source = source,
        )
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_aapsclient_restart_sent))
    }
}
