package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import org.json.JSONObject

class AapsClientRestartAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        plugin.rxBus.send(EventNSClientRestart())
        uel.log(
            action = Action.START_AAPS,
            note = tagName,
            source = source,
        )
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_aapsclient_restart_sent))
    }
}
