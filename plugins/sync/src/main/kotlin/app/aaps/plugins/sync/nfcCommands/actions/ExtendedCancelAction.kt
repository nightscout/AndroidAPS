package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.icons.IcCancelExtendedBolus
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R

class ExtendedCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_extended_stop
    override val elementType = ElementType.EXTENDED_BOLUS
    override val argType = listOf<ArgType>()
    override val icon = IcCancelExtendedBolus

    override suspend fun execute(): NfcExecutionResult {
        val result = plugin.commandQueue.cancelExtended()
        return if (result.success) {
            uel.log(
                action = Action.CANCEL_EXTENDED_BOLUS,
                source = source,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
            )
            NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_extended_canceled))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "cancelExtended failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
