package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

abstract class NfcAction(protected val plugin: NfcCommandsPlugin) {
    
    abstract suspend fun execute(params: JSONObject): NfcExecutionResult

    protected fun invalidFormat(): NfcExecutionResult = 
        NfcExecutionResult(false, plugin.rh.gs(R.string.wrong_format))

    protected fun commandNotPossible(): NfcExecutionResult = 
        NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_command_not_possible))
}
