package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Sources
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

abstract class NfcAction(protected val plugin: NfcCommandsPlugin) {
    
    protected val uel = plugin.uel
    protected val source = Sources.NfcCommands

    abstract suspend fun execute(params: JSONObject, tagName: String? = null): NfcExecutionResult

    open suspend fun formatParams(params: JSONObject): String? = null

    protected fun invalidFormat(): NfcExecutionResult = 
        NfcExecutionResult(false, plugin.rh.gs(R.string.wrong_format))

    protected fun commandNotPossible(): NfcExecutionResult = 
        NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_command_not_possible))
}
