package app.aaps.plugins.main.general.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.plugins.main.general.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.main.general.nfcCommands.NfcExecutionResult
import app.aaps.plugins.main.R

class TempTargetCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    override suspend fun execute(divided: List<String>): NfcExecutionResult {
        plugin.persistenceLayer.cancelCurrentTemporaryTargetIfAny(
            timestamp = plugin.dateUtil.now(),
            action = Action.CANCEL_TT,
            source = Sources.NfcCommands,
            note = plugin.rh.gs(R.string.nfccommands_tt_canceled),
            listValues = listOf(ValueWithUnit.SimpleString(plugin.rh.gsNotLocalised(R.string.nfccommands_tt_canceled))),
        )
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_tt_canceled))
    }
}
