package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class AapsClientRestartAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        if (divided.size != 2) return invalidFormat()
        if (divided[1].equals("RESTART", ignoreCase = true)) {
            plugin.rxBus.send(EventNSClientRestart())
            return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_aapsclient_restart_sent))
        }
        return invalidFormat()
    }
}
