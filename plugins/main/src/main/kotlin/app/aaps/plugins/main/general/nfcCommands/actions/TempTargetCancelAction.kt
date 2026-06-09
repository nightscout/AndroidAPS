package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R
import org.json.JSONObject

class TempTargetCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(params: JSONObject, tagName: String?): NfcExecutionResult {
        plugin.persistenceLayer.cancelCurrentTemporaryTargetIfAny(
            timestamp = plugin.dateUtil.now(),
            action = Action.CANCEL_TT,
            source = source,
            note = plugin.rh.gs(R.string.nfccommands_tt_canceled),
            listValues = listOf(ValueWithUnit.SimpleString(plugin.rh.gsNotLocalised(R.string.nfccommands_tt_canceled))),
        )
        uel.log(
            action = Action.CANCEL_TT,
            source = source,
            note = tagName
        )
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_tt_canceled))
    }
}
