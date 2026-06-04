package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class AapsClientRestartAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject): NfcExecutionResult {
        plugin.rxBus.send(EventNSClientRestart())
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_aapsclient_restart_sent))
    }
}
