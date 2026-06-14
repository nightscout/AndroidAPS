package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.icons.IcTbrCancel
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.core.ui.R as CoreUiR

class BasalCancelAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.cancel_temp
    override val elementType = ElementType.TEMP_BASAL
    override val argType = listOf<ArgType>()
    override val icon = IcTbrCancel

    override suspend fun execute(): NfcExecutionResult {
        val result = plugin.commandQueue.cancelTempBasal(enforceNew = true)
        return if (result.success) {
            uel.log(
                action = Action.CANCEL_TEMP_BASAL,
                note = params.optString(NfcJsonKeys.TAG_NAME, ""),
                source = source,
            )
            NfcExecutionResult(true, plugin.rh.gs(CoreUiR.string.stoptemptarget))
        } else {
            plugin.aapsLogger.error(LTag.NFC, "cancelTempBasal failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
